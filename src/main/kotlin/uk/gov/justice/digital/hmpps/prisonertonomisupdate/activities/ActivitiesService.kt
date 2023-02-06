package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateOffenderProgramProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.EndOffenderProgramProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PayRateRequest
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class ActivitiesService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val activitiesUpdateQueueService: ActivitiesUpdateQueueService,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createActivity(event: ScheduleDomainEvent) {
    activitiesApiService.getActivitySchedule(event.additionalInformation.activityScheduleId).run {
      val activity = activitiesApiService.getActivity(activity.id)

      val telemetryMap = mutableMapOf(
        "activityScheduleId" to id.toString(),
        "description" to description,
      )

      // to protect against repeated create messages for same activity
      if (mappingService.getMappingGivenActivityScheduleIdOrNull(activity.id) != null) {
        log.warn("Mapping already exists for activity schedule id ${activity.id}")
        return
      }

      val nomisResponse = try {
        nomisApiService.createActivity(toNomisActivity(this, activity))
      } catch (e: Exception) {
        telemetryClient.trackEvent("activity-create-failed", telemetryMap)
        log.error("createActivity() Unexpected exception", e)
        throw e
      }

      telemetryMap["courseActivityId"] = nomisResponse.courseActivityId.toString()

      try {
        mappingService.createMapping(
          ActivityMappingDto(
            nomisCourseActivityId = nomisResponse.courseActivityId,
            activityScheduleId = activity.id,
            mappingType = "ACTIVITY_CREATED",
          )
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("activity-create-map-failed", telemetryMap)
        log.error("Unexpected exception, queueing retry", e)
        activitiesUpdateQueueService.sendMessage(
          ActivityContext(
            nomisCourseActivityId = nomisResponse.courseActivityId,
            activityScheduleId = activity.id,
          )
        )
        return
      }

      telemetryClient.trackEvent("activity-created-event", telemetryMap)
    }
  }

  fun createAllocation(allocationEvent: AllocationDomainEvent) {
    activitiesApiService.getAllocation(allocationEvent.additionalInformation.allocationId).let { allocation ->
      mappingService.getMappingGivenActivityScheduleId(allocationEvent.additionalInformation.scheduleId).let { mapping ->

        val telemetryMap = mutableMapOf(
          "allocationId" to allocation.id.toString(),
          "offenderNo" to allocation.prisonerNumber,
          "bookingId" to allocation.bookingId.toString(),
        )

        val nomisResponse = try {
          nomisApiService.createAllocation(
            mapping.nomisCourseActivityId,
            CreateOffenderProgramProfileRequest(
              bookingId = allocation.bookingId!!,
              startDate = allocation.startDate,
              endDate = allocation.endDate,
              payBandCode = allocation.payBand!!, // Nomis appears to always require a payband
            )
          )
        } catch (e: Exception) {
          telemetryClient.trackEvent("activity-allocation-create-failed", telemetryMap)
          throw e
        }

        telemetryMap["offenderProgramReferenceId"] = nomisResponse.offenderProgramReferenceId.toString()

        telemetryClient.trackEvent("activity-allocation-created-event", telemetryMap)
      }
    }
  }

  fun deallocate(allocationEvent: AllocationDomainEvent) {
    activitiesApiService.getAllocation(allocationEvent.additionalInformation.allocationId).let { allocation ->
      mappingService.getMappingGivenActivityScheduleId(allocationEvent.additionalInformation.scheduleId)
        .let { mapping ->

          val telemetryMap = mutableMapOf(
            "allocationId" to allocation.id.toString(),
            "offenderNo" to allocation.prisonerNumber,
            "bookingId" to allocation.bookingId.toString(),
          )

          val nomisResponse = try {
            nomisApiService.deallocate(
              mapping.nomisCourseActivityId,
              allocation.bookingId!!,
              EndOffenderProgramProfileRequest(
                endDate = allocation.endDate!!,
                endReason = allocation.deallocatedReason, // TODO probably will need a mapping
                // endComment = allocation.?, // TODO could put something useful in here
              )
            )
          } catch (e: Exception) {
            telemetryClient.trackEvent("activity-deallocate-failed", telemetryMap)
            throw e
          }

          telemetryMap["offenderProgramReferenceId"] = nomisResponse.offenderProgramReferenceId.toString()

          telemetryClient.trackEvent("activity-deallocate-event", telemetryMap)
        }
    }
  }

  private fun toNomisActivity(schedule: ActivitySchedule, activity: Activity): CreateActivityRequest {
    return CreateActivityRequest(
      code = "$activity.id-$schedule.id",
      startDate = activity.startDate,
      endDate = activity.endDate,
      prisonId = activity.prisonCode,
      internalLocationId = schedule.internalLocation!!.id.toLong(),
      capacity = schedule.capacity,
      payRates = activity.pay.map { p ->
        PayRateRequest(
          incentiveLevel = p.incentiveLevel!!,
          payBand = p.payBand!!,
          rate = BigDecimal(p.rate!!).movePointLeft(2)
        )
      },
      description = "${activity.description} - ${schedule.description}",
      minimumIncentiveLevelCode = activity.minimumIncentiveLevel,
      programCode = activity.category.code,
    )
  }

  fun createRetry(context: ActivityContext) {
    mappingService.createMapping(
      ActivityMappingDto(
        nomisCourseActivityId = context.nomisCourseActivityId,
        activityScheduleId = context.activityScheduleId,
        mappingType = "ACTIVITY_CREATED",
      )
    )
  }
}

data class ScheduleDomainEvent(
  val eventType: String,
  val additionalInformation: ScheduleAdditionalInformation,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
)

data class ScheduleAdditionalInformation(
  val activityScheduleId: Long,
)

data class AllocationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
  val additionalInformation: AllocationAdditionalInformation,
)

data class AllocationAdditionalInformation(
  val scheduleId: Long,
  val allocationId: Long,
)
