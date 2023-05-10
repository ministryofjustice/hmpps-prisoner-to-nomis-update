package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
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
      bookingId = allocation.bookingId!!, // TODO SDIT-438 this should not be nullable - waiting for fix to Activities endpoint
      payBandCode = allocation.prisonPayBand.nomisPayBand.toString(),
      startDate = allocation.startDate,
      endDate = allocation.endDate,
      endReason = "PRG_END", // TODO SDIT-438 waiting for the Activities team to provide reason codes that we can map to Nomis reason codes (reference coe domain PS_END_RSN)
      endComment = "Deallocated in DPS by ${allocation.deallocatedBy} at ${allocation.deallocatedTime?.format(humanTimeFormat)}",
      suspended = false, // TODO SDIT-438 waiting for the Activities team to expose the suspended details via their API
      suspendedComment = null,
    )
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
