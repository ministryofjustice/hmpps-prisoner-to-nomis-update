package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityPay
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateOffenderProgramProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.EndOffenderProgramProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PayRateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ScheduleRuleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
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
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createActivity(event: ScheduleDomainEvent) {
    synchronise {
      name = "activity"
      telemetryClient = this@ActivitiesService.telemetryClient
      retryQueueService = activitiesUpdateQueueService
      eventTelemetry = mapOf(
        "activityScheduleId" to event.additionalInformation.activityScheduleId.toString(),
      )

      checkMappingDoesNotExist {
        mappingService.getMappingGivenActivityScheduleIdOrNull(event.additionalInformation.activityScheduleId)
      }
      transform {
        activitiesApiService.getActivitySchedule(event.additionalInformation.activityScheduleId).let { activitySchedule ->
          eventTelemetry += "description" to activitySchedule.description

          createTransformedActivity(activitySchedule).let { nomisResponse ->
            ActivityMappingDto(
              nomisCourseActivityId = nomisResponse.courseActivityId,
              activityScheduleId = event.additionalInformation.activityScheduleId,
              mappingType = "ACTIVITY_CREATED",
            )
          }
        }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  private fun createTransformedActivity(activitySchedule: ActivitySchedule) =
    activitiesApiService.getActivity(activitySchedule.activity.id).let {
      nomisApiService.createActivity(toNomisActivity(activitySchedule, it))
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

  fun createRetry(context: CreateMappingRetryMessage<ActivityContext>) {
    mappingService.createMapping(
      ActivityMappingDto(
        nomisCourseActivityId = context.mapping.nomisCourseActivityId,
        activityScheduleId = context.mapping.activityScheduleId,
        mappingType = "ACTIVITY_CREATED",
      ),
    ).also {
      telemetryClient.trackEvent(
        "activity-retry-success",
        mapOf("activityScheduleId" to context.mapping.activityScheduleId.toString()),
      )
    }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())
  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
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
