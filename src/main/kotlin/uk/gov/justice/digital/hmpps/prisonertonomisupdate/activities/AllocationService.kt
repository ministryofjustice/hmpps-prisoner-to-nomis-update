package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation.Status.eNDED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class AllocationService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val telemetryClient: TelemetryClient,
) {

  private val humanTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  suspend fun upsertAllocation(allocationEvent: AllocationDomainEvent) {
    val telemetryMap = mutableMapOf(
      "dpsAllocationId" to allocationEvent.additionalInformation.allocationId.toString(),
    )
    runCatching {
      activitiesApiService.getAllocation(allocationEvent.additionalInformation.allocationId).let { allocation ->
        telemetryMap["offenderNo"] = allocation.prisonerNumber
        telemetryMap["bookingId"] = allocation.bookingId.toString()
        telemetryMap["dpsActivityScheduleId"] = allocation.scheduleId.toString()
        mappingService.getMappingGivenActivityScheduleId(allocation.scheduleId)
          .also { telemetryMap["nomisCourseActivityId"] = it.nomisCourseActivityId.toString() }
          .let { mapping ->
            nomisApiService.upsertAllocation(
              mapping.nomisCourseActivityId,
              toUpsertAllocationRequest(allocation),
            ).also {
              telemetryMap["nomisAllocationId"] = it.offenderProgramReferenceId.toString()
            }
          }
      }
    }.onSuccess {
      telemetryClient.trackEvent("activity-allocation-success", telemetryMap)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-allocation-failed", telemetryMap)
      throw e
    }
  }

  private fun toUpsertAllocationRequest(allocation: Allocation) =
    UpsertAllocationRequest(
      bookingId = allocation.bookingId,
      payBandCode = allocation.prisonPayBand.nomisPayBand.toString(),
      startDate = allocation.startDate,
      endDate = allocation.endDate,
      endReason = getEndReason(allocation.status),
      endComment = getEndComment(allocation.status, allocation.deallocatedBy, allocation.deallocatedTime, allocation.deallocatedReason),
      suspended = isSuspended(allocation.status),
      suspendedComment = getSuspendedComment(allocation.status, allocation.suspendedBy, allocation.suspendedTime, allocation.suspendedReason),
    )

  private fun getEndReason(status: Allocation.Status) =
    if (status == eNDED) "PRG_END" else null // TODO SDIT-438 waiting for the Activities team to provide reason codes that we can map to Nomis reason codes (reference coe domain PS_END_RSN)

  private fun getEndComment(status: Allocation.Status, by: String?, time: LocalDateTime?, reason: String?) =
    if (status == eNDED) {
      "Deallocated in DPS by $by at ${time?.format(humanTimeFormat)} for reason $reason"
    } else {
      null
    }

  private fun isSuspended(status: Allocation.Status) =
    listOf(Allocation.Status.sUSPENDED, Allocation.Status.aUTOSUSPENDED).contains(status)

  private fun getSuspendedComment(status: Allocation.Status, by: String?, time: LocalDateTime?, reason: String?) =
    if (isSuspended(status)) {
      "Suspended in DPS by $by at ${time?.format(humanTimeFormat)} for reason $reason"
    } else {
      null
    }
}

data class AllocationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
  val additionalInformation: AllocationAdditionalInformation,
)

data class AllocationAdditionalInformation(
  val allocationId: Long,
)
