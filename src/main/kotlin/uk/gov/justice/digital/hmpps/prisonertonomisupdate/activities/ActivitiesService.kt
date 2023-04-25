package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityPay
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PayRateRequest
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

  private suspend fun createTransformedActivity(activitySchedule: ActivitySchedule) =
    activitiesApiService.getActivity(activitySchedule.activity.id).let {
      nomisApiService.createActivity(toNomisActivity(activitySchedule, it))
    }

  suspend fun updateActivity(event: ScheduleDomainEvent) {
    val activityScheduleId = event.additionalInformation.activityScheduleId
    val telemetryMap = mutableMapOf("activityScheduleId" to activityScheduleId.toString())

    runCatching {
      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(activityScheduleId).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      val activitySchedule = activitiesApiService.getActivitySchedule(activityScheduleId)

      val activity = activitiesApiService.getActivity(activitySchedule.activity.id)
        .also { telemetryMap["activityId"] = it.id.toString() }

      activitySchedule.toUpdateActivityRequest(activity.pay)
        .also { nomisApiService.updateActivity(nomisCourseActivityId, it) }
    }.onSuccess {
      telemetryClient.trackEvent("activity-amend-success", telemetryMap, null)
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

  private fun toNomisActivity(schedule: ActivitySchedule, activity: Activity): CreateActivityRequest {
    return CreateActivityRequest(
      code = schedule.id.toString(),
      startDate = activity.startDate,
      endDate = activity.endDate,
      prisonId = activity.prisonCode,
      internalLocationId = schedule.internalLocation?.id?.toLong(),
      capacity = schedule.capacity,
      payRates = activity.pay.toPayRateRequests(),
      description = toNomisActivityDescription(activity.summary),
      minimumIncentiveLevelCode = activity.minimumIncentiveNomisCode,
      programCode = activity.category.code,
      payPerSession = activity.payPerSession.value,
      schedules = schedule.instances.toScheduleRequests(),
      scheduleRules = schedule.slots.toScheduleRuleRequests(),
      excludeBankHolidays = !schedule.runsOnBankHoliday, // Nomis models the negative (exclude) and Activities models the positive (runs on)
    )
  }

  private fun toNomisActivityDescription(activityDescription: String): String =
    "SAA " + activityDescription.substring(0, min(36, activityDescription.length))

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

  suspend fun createRetry(context: CreateMappingRetryMessage<ActivityContext>) {
    mappingService.createMapping(
      ActivityMappingDto(
        nomisCourseActivityId = context.mapping.nomisCourseActivityId,
        activityScheduleId = context.mapping.activityScheduleId,
        mappingType = "ACTIVITY_CREATED",
      ),
    ).also {
      telemetryClient.trackEvent(
        "activity-create-mapping-retry-success",
        mapOf("activityScheduleId" to context.mapping.activityScheduleId.toString()),
      )
    }
  }

  suspend fun deleteAllActivities() {
    mappingService.getAllMappings().forEach { mapping ->
      runCatching {
        nomisApiService.deleteActivity(mapping.nomisCourseActivityId)
        mappingService.deleteMapping(mapping.activityScheduleId)
      }.onSuccess {
        telemetryClient.trackEvent(
          "activity-DELETE-ALL-success",
          mapOf(
            "activityScheduleId" to mapping.activityScheduleId.toString(),
            "nomisCourseActivityId" to mapping.nomisCourseActivityId.toString(),
          ),
          null,
        )
      }.onFailure { e ->
        log.error("Failed to delete activity with id ${mapping.nomisCourseActivityId}", e)
        telemetryClient.trackEvent(
          "activity-DELETE-ALL-failed",
          mapOf(
            "activityScheduleId" to mapping.activityScheduleId.toString(),
            "nomisCourseActivityId" to mapping.nomisCourseActivityId.toString(),
          ),
          null,
        )
      }
    }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())
  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
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
