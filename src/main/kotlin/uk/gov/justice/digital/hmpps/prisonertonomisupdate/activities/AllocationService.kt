package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime

@Service
class AllocationService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun createAllocation(allocationEvent: AllocationDomainEvent) {
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
            nomisApiService.createAllocation(
              mapping.nomisCourseActivityId,
              CreateAllocationRequest(
                bookingId = allocation.bookingId!!,
                startDate = allocation.startDate,
                endDate = allocation.endDate,
                payBandCode = allocation.prisonPayBand.nomisPayBand.toString(),
              ),
            ).also {
              telemetryMap["nomisAllocationId"] = it.offenderProgramReferenceId.toString()
            }
          }
      }
    }.onSuccess {
      telemetryClient.trackEvent("activity-allocation-create-success", telemetryMap)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-allocation-create-failed", telemetryMap)
      throw e
    }
  }

  suspend fun deallocate(allocationEvent: AllocationDomainEvent) {
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
            nomisApiService.deallocate(
              mapping.nomisCourseActivityId,
              UpdateAllocationRequest(
                bookingId = allocation.bookingId!!,
                endDate = allocation.endDate!!,
                // TODO SDIT-421 probably will need a mapping
                // Currently (22/3/2023) the only applicable reason is that the end date has been reached.
                endReason = "PRG_END",
                endComment = allocation.deallocatedReason,
              ),
            ).also {
              telemetryMap["nomisAllocationId"] = it.offenderProgramReferenceId.toString()
            }
          }
      }
    }.onSuccess {
      telemetryClient.trackEvent("activity-deallocate-success", telemetryMap)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-deallocate-failed", telemetryMap)
      throw e
    }
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
