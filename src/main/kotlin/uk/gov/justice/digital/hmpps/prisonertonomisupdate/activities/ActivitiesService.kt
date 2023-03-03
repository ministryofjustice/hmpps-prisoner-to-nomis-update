package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityPay
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateOffenderProgramProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.EndOffenderProgramProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PayRateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ScheduleRuleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateActivityRequest
import java.lang.Integer.min
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class ActivitiesService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val activitiesUpdateQueueService: ActivitiesUpdateQueueService,
  private val telemetryClient: TelemetryClient,
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
      if (mappingService.getMappingGivenActivityScheduleIdOrNull(id) != null) {
        log.warn("Mapping already exists for activity schedule id $id")
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
            activityScheduleId = id,
            mappingType = "ACTIVITY_CREATED",
          ),
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("activity-create-map-failed", telemetryMap)
        log.error("Unexpected exception, queueing retry", e)
        activitiesUpdateQueueService.sendMessage(
          ActivityContext(
            nomisCourseActivityId = nomisResponse.courseActivityId,
            activityScheduleId = id,
          ),
        )
        return
      }

      telemetryClient.trackEvent("activity-created-event", telemetryMap)
    }
  }

  fun updateActivity(event: ScheduleDomainEvent) {
    val telemetryMap = mutableMapOf("activityScheduleId" to event.additionalInformation.activityScheduleId.toString())

    runCatching {
      val activitySchedule = activitiesApiService.getActivitySchedule(event.additionalInformation.activityScheduleId)

      val activity = activitiesApiService.getActivity(activitySchedule.activity.id)
        .also { telemetryMap["activityId"] = it.id.toString() }

      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(activitySchedule.id).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      activitySchedule.toUpdateActivityRequest(activity.pay)
        .also { nomisApiService.updateActivity(nomisCourseActivityId, it) }
    }.onSuccess {
      telemetryClient.trackEvent("activity-amend-event", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-amend-failed", telemetryMap, null)
      throw e
    }
  }

  private fun ActivitySchedule.toUpdateActivityRequest(pay: List<ActivityPay>) =
    UpdateActivityRequest(
      endDate = endDate,
      internalLocationId = internalLocation?.id?.toLong(),
      payRates = pay.toPayRateRequests(),
      scheduleRules = slots.toScheduleRuleRequests(),
    )

  fun updateScheduleInstances(amendInstancesEvent: ScheduleDomainEvent) {
    val telemetryMap = mutableMapOf("activityScheduleId" to amendInstancesEvent.additionalInformation.activityScheduleId.toString())

    runCatching {
      val activitySchedule = activitiesApiService.getActivitySchedule(amendInstancesEvent.additionalInformation.activityScheduleId)

      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(activitySchedule.id).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      activitySchedule.instances.toScheduleRequests()
        .also { nomisApiService.updateScheduleInstances(nomisCourseActivityId, it) }
    }.onSuccess {
      telemetryClient.trackEvent("schedule-instances-amend-event", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("schedule-instances-amend-failed", telemetryMap, null)
      throw e
    }
  }

  private fun List<ScheduledInstance>.toScheduleRequests() =
    map {
      ScheduleRequest(
        date = it.date,
        startTime = LocalTime.parse(it.startTime),
        endTime = LocalTime.parse(it.endTime),
      )
    }

  fun createAllocation(allocationEvent: AllocationDomainEvent) {
    activitiesApiService.getAllocation(allocationEvent.additionalInformation.allocationId).let { allocation ->
      mappingService.getMappingGivenActivityScheduleId(allocation.scheduleId).let { mapping ->

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
              payBandCode = allocation.prisonPayBand.nomisPayBand.toString(),
            ),
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
      mappingService.getMappingGivenActivityScheduleId(allocation.scheduleId)
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
                endReason = allocation.deallocatedReason, // TODO SDI-615 probably will need a mapping
                // endComment = allocation.?, // TODO SDI-615 could put something useful in here
              ),
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
      code = schedule.id.toString(),
      startDate = activity.startDate,
      endDate = activity.endDate,
      prisonId = activity.prisonCode,
      internalLocationId = schedule.internalLocation?.id?.toLong(),
      capacity = schedule.capacity,
      payRates = activity.pay.toPayRateRequests(),
      description = toNomisActivityDescription(activity.summary, schedule.description),
      minimumIncentiveLevelCode = activity.minimumIncentiveNomisCode,
      programCode = activity.category.code,
      payPerSession = activity.payPerSession.value,
      schedules = schedule.instances.toScheduleRequests(),
      scheduleRules = schedule.slots.toScheduleRuleRequests(),
    )
  }

  private fun toNomisActivityDescription(activityDescription: String, scheduleDescription: String): String {
    var description = "SAA $activityDescription"
      .let { it.substring(0, min(it.length, 40)).trim() }
    if (description.length < 39) {
      description += " ${scheduleDescription.let { it.substring(0, min(it.length, 39 - description.length)) }}"
    }
    return description
  }

  private fun List<ActivityScheduleSlot>.toScheduleRuleRequests(): List<ScheduleRuleRequest> =
    map { slot ->
      ScheduleRuleRequest(
        startTime = LocalTime.parse(slot.startTime),
        endTime = LocalTime.parse(slot.endTime),
        monday = slot.mondayFlag,
        tuesday = slot.tuesdayFlag,
        wednesday = slot.wednesdayFlag,
        thursday = slot.thursdayFlag,
        friday = slot.fridayFlag,
        saturday = slot.saturdayFlag,
        sunday = slot.sundayFlag,
      )
    }

  private fun List<ActivityPay>.toPayRateRequests(): List<PayRateRequest> =
    map { p ->
      PayRateRequest(
        incentiveLevel = p.incentiveNomisCode,
        payBand = p.prisonPayBand.nomisPayBand.toString(),
        rate = BigDecimal(p.rate!!).movePointLeft(2),
      )
    }

  fun createRetry(context: ActivityContext) {
    mappingService.createMapping(
      ActivityMappingDto(
        nomisCourseActivityId = context.nomisCourseActivityId,
        activityScheduleId = context.activityScheduleId,
        mappingType = "ACTIVITY_CREATED",
      ),
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
  val allocationId: Long,
)
