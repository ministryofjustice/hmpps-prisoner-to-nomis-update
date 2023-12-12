package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation.Status.AUTO_SUSPENDED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation.Status.ENDED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation.Status.SUSPENDED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Slot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AllocationExclusion
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

  suspend fun upsertAllocationEvent(allocationEvent: AllocationDomainEvent) {
    upsertAllocation(allocationEvent.additionalInformation.allocationId)
  }

  suspend fun upsertAllocation(allocationId: Long) {
    val telemetryMap = mutableMapOf(
      "dpsAllocationId" to allocationId.toString(),
    )
    runCatching {
      activitiesApiService.getAllocation(allocationId).let { allocation ->
        telemetryMap["offenderNo"] = allocation.prisonerNumber
        telemetryMap["bookingId"] = allocation.bookingId.toString()
        telemetryMap["dpsActivityScheduleId"] = allocation.scheduleId.toString()
        mappingService.getMappings(allocation.scheduleId)
          .also { telemetryMap["nomisCourseActivityId"] = it.nomisCourseActivityId.toString() }
          .let { mapping ->
            nomisApiService.upsertAllocation(
              mapping.nomisCourseActivityId,
              toUpsertAllocationRequest(allocation),
            ).also {
              telemetryMap["nomisAllocationId"] = it.offenderProgramReferenceId.toString()
              telemetryMap["prisonId"] = it.prisonId
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
      payBandCode = allocation.prisonPayBand?.nomisPayBand?.toString(),
      startDate = allocation.startDate,
      endDate = allocation.endDate,
      endReason = getEndReason(allocation.status),
      endComment = getEndComment(allocation.status, allocation.deallocatedBy, allocation.deallocatedTime, allocation.deallocatedReason?.description),
      suspended = isSuspended(allocation.status),
      suspendedComment = getSuspendedComment(allocation.status, allocation.suspendedBy, allocation.suspendedTime, allocation.suspendedReason),
      programStatusCode = getProgramStatus(allocation.status),
      exclusions = toAllocationExclusions(allocation.exclusions ?: listOf()),
    )

  private fun getEndReason(status: Allocation.Status) =
    if (status == ENDED) "OTH" else null

  private fun getEndComment(status: Allocation.Status, by: String?, time: LocalDateTime?, reason: String?) =
    if (status == ENDED) {
      "Deallocated in DPS by $by at ${time?.format(humanTimeFormat)} for reason $reason"
    } else {
      null
    }

  private fun isSuspended(status: Allocation.Status) = status in listOf(SUSPENDED, AUTO_SUSPENDED)

  private fun getSuspendedComment(status: Allocation.Status, by: String?, time: LocalDateTime?, reason: String?) =
    if (isSuspended(status)) {
      "Suspended in DPS by $by at ${time?.format(humanTimeFormat)} for reason $reason"
    } else {
      null
    }

  private fun getProgramStatus(status: Allocation.Status) =
    when (status) {
      ENDED -> "END"
      else -> "ALLOC"
    }

  /*
   * Convert DPS exclusion slots to NOMIS exclusions.
   */
  fun toAllocationExclusions(exclusions: List<Slot>): List<AllocationExclusion> =
    exclusions
      .flatMap { dpsSlot -> dpsSlot.daysOfWeek.map { day -> AllocationExclusion(day.toNomis(), dpsSlot.timeSlot.toNomis()) } }
      .consolidateFullDays()

  private fun Slot.DaysOfWeek.toNomis() = AllocationExclusion.Day.entries.first { value.startsWith(it.value) }
  private fun String.toNomis() = AllocationExclusion.Slot.entries.first { this == it.value }

  /*
   * In NOMIS a full day is represented by a null slot rather than a record for each slot.
   */
  private fun List<AllocationExclusion>.consolidateFullDays() =
    groupBy { exclusion -> exclusion.day }
      .mapValues { dayExclusions -> dayExclusions.value.map { exclusion -> exclusion.slot } }
      .flatMap { (day, slots) ->
        if (slots.isAllDay()) {
          listOf(AllocationExclusion(day, null))
        } else {
          slots.map { slot -> AllocationExclusion(day, slot) }
        }
      }

  private fun List<AllocationExclusion.Slot?>.isAllDay() = containsAll(AllocationExclusion.Slot.entries)
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
