package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateAllocationRequest
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
      "allocationId" to allocationEvent.additionalInformation.allocationId.toString(),
    )
    runCatching {
      activitiesApiService.getAllocation(allocationEvent.additionalInformation.allocationId).let { allocation ->
        telemetryMap["offenderNo"] = allocation.prisonerNumber
        telemetryMap["bookingId"] = allocation.bookingId.toString()
        mappingService.getMappingGivenActivityScheduleId(allocation.scheduleId).let { mapping ->
          nomisApiService.createAllocation(
            mapping.nomisCourseActivityId,
            CreateAllocationRequest(
              bookingId = allocation.bookingId!!,
              startDate = allocation.startDate,
              endDate = allocation.endDate,
              payBandCode = allocation.prisonPayBand.nomisPayBand.toString(),
            ),
          ).also {
            telemetryMap["offenderProgramReferenceId"] = it.offenderProgramReferenceId.toString()
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
      "allocationId" to allocationEvent.additionalInformation.allocationId.toString(),
    )

    runCatching {
      activitiesApiService.getAllocation(allocationEvent.additionalInformation.allocationId).let { allocation ->
        telemetryMap["offenderNo"] = allocation.prisonerNumber
        telemetryMap["bookingId"] = allocation.bookingId.toString()
        mappingService.getMappingGivenActivityScheduleId(allocation.scheduleId)
          .let { mapping ->
            nomisApiService.deallocate(
              mapping.nomisCourseActivityId,
              UpdateAllocationRequest(
                bookingId = allocation.bookingId!!,
                endDate = allocation.endDate!!,
                endReason = allocation.deallocatedReason, // TODO SDIT-421 probably will need a mapping
                // endComment = allocation.?, // TODO SDIT-421 could put something useful in here
              ),
            ).also {
              telemetryMap["offenderProgramReferenceId"] = it.offenderProgramReferenceId.toString()
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
