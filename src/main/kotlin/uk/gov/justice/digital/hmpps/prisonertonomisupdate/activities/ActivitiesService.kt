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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateActivityResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledInstanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PayRateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ScheduleRuleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.lang.Integer.min
import java.math.BigDecimal
import java.time.LocalTime

@Service
class ActivitiesService(
  private val activitiesApiService: ActivitiesApiService,
  private val activitiesNomisApiService: ActivitiesNomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val activitiesRetryQueueService: ActivitiesRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createActivityEvent(event: ScheduleDomainEvent) {
    createActivity(event.additionalInformation.activityScheduleId)
  }

  suspend fun createActivity(activityScheduleId: Long) {
    synchronise {
      name = "activity"
      telemetryClient = this@ActivitiesService.telemetryClient
      retryQueueService = activitiesRetryQueueService
      eventTelemetry = mapOf(
        "dpsActivityScheduleId" to activityScheduleId.toString(),
      )

      checkMappingDoesNotExist {
        mappingService.getMappingsOrNull(activityScheduleId)
      }
      transform {
        activitiesApiService.getActivitySchedule(activityScheduleId)
          .let { activitySchedule ->
            eventTelemetry += "description" to activitySchedule.description
            eventTelemetry += "dpsActivityId" to activitySchedule.activity.id.toString()
            eventTelemetry += "prisonId" to activitySchedule.activity.prisonCode

            createTransformedActivity(activitySchedule)
              .also { eventTelemetry += "nomisCourseActivityId" to it.courseActivityId.toString() }
              .let { nomisResponse -> buildActivityMappingDto(nomisResponse, activitySchedule) }
          }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  private fun buildActivityMappingDto(
    nomisResponse: CreateActivityResponse,
    activitySchedule: ActivitySchedule,
    existingMappings: ActivityMappingDto? = null,
    mappingType: String = "ACTIVITY_CREATED",
  ): ActivityMappingDto = nomisResponse.courseSchedules.map { nomisSchedule ->
    ActivityScheduleMappingDto(
      scheduledInstanceId = nomisSchedule.findScheduledInstanceId(activitySchedule.instances, existingMappings),
      nomisCourseScheduleId = nomisSchedule.courseScheduleId,
      mappingType = mappingType,
    )
  }
    .let {
      ActivityMappingDto(
        nomisCourseActivityId = nomisResponse.courseActivityId,
        activityScheduleId = activitySchedule.id,
        activityId = activitySchedule.activity.id,
        mappingType = mappingType,
        scheduledInstanceMappings = it,
      )
    }

  private fun CreateScheduledInstanceResponse.findScheduledInstanceId(
    activityScheduledInstances: List<ScheduledInstance>,
    existingMappings: ActivityMappingDto?,
  ) = existingMappings.findScheduledInstanceId(courseScheduleId)
    ?: let { activityScheduledInstances.findScheduledInstanceId(this) }
    ?: throw IllegalStateException("Unable to find an Activities scheduled instance for the Nomis course schedule - this should not happen: ${this.courseScheduleId}")

  private fun ActivityMappingDto?.findScheduledInstanceId(nomisCourseScheduleId: Long) = this?.scheduledInstanceMappings
    ?.find { nomisCourseScheduleId == it.nomisCourseScheduleId }
    ?.scheduledInstanceId

  private fun List<ScheduledInstance>.findScheduledInstanceId(nomisCourseSchedule: CreateScheduledInstanceResponse) = find { instance ->
    instance.date == nomisCourseSchedule.date &&
      instance.startTime.toLocalTime() == nomisCourseSchedule.startTime.toLocalTime() &&
      instance.endTime.toLocalTime() == nomisCourseSchedule.endTime.toLocalTime()
  }?.id

  private fun String.toLocalTime() = LocalTime.parse(this)
  private suspend fun createTransformedActivity(activitySchedule: ActivitySchedule) = activitiesApiService.getActivity(activitySchedule.activity.id).let {
    activitiesNomisApiService.createActivity(toCreateActivityRequest(activitySchedule, it))
  }

  suspend fun updateActivityEvent(event: ScheduleDomainEvent) {
    updateActivity(event.additionalInformation.activityScheduleId)
  }

  suspend fun updateActivity(activityScheduleId: Long) {
    val telemetryMap = mutableMapOf("dpsActivityScheduleId" to activityScheduleId.toString())

    runCatching {
      val mappings = mappingService.getMappings(activityScheduleId)
        .also { telemetryMap["nomisCourseActivityId"] = it.nomisCourseActivityId.toString() }

      val activitySchedule = activitiesApiService.getActivitySchedule(activityScheduleId)
        .also {
          telemetryMap["dpsActivityId"] = it.activity.id.toString()
          telemetryMap["prisonId"] = it.activity.prisonCode
        }

      val activity = activitiesApiService.getActivity(activitySchedule.activity.id)

      activitySchedule.toUpdateActivityRequest(activity.pay, activity.category.code, activity.outsideWork, mappings)
        .let { activitiesNomisApiService.updateActivity(mappings.nomisCourseActivityId, it) }
        .let { nomisResponse -> buildActivityMappingDto(nomisResponse, activitySchedule, mappings, "ACTIVITY_UPDATED") }
        .also { mappingRequest -> mappingService.updateMapping(mappingRequest) }
    }.onSuccess {
      telemetryClient.trackEvent("activity-amend-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-amend-failed", telemetryMap, null)
      throw e
    }
  }

  private suspend fun ActivitySchedule.toUpdateActivityRequest(pay: List<ActivityPay>, categoryCode: String, outsideWork: Boolean, mappings: ActivityMappingDto) = UpdateActivityRequest(
    startDate = startDate,
    capacity = capacity,
    payRates = pay.toPayRateRequests(),
    description = toNomisActivityDescription(description),
    payPerSession = toUpdatePayPerSession(),
    scheduleRules = slots.toScheduleRuleRequests(),
    schedules = instances.toCourseScheduleRequests(mappings.scheduledInstanceMappings),
    excludeBankHolidays = !runsOnBankHoliday,
    outsideWork = outsideWork,
    endDate = endDate,
    internalLocationId = internalLocation?.let { mappingService.getLocationMappingGivenDpsId(internalLocation.dpsLocationId!!).nomisLocationId },
    programCode = categoryCode,
  )

  private suspend fun toCreateActivityRequest(schedule: ActivitySchedule, activity: Activity): CreateActivityRequest = CreateActivityRequest(
    code = schedule.id.toString(),
    startDate = activity.startDate,
    endDate = activity.endDate,
    prisonId = activity.prisonCode,
    internalLocationId = schedule.internalLocation?.let { mappingService.getLocationMappingGivenDpsId(schedule.internalLocation.dpsLocationId!!).nomisLocationId },
    capacity = schedule.capacity,
    payRates = activity.pay.toPayRateRequests(),
    description = toNomisActivityDescription(activity.summary),
    programCode = activity.category.code,
    payPerSession = schedule.toCreatePayPerSession(),
    schedules = schedule.instances.toCourseScheduleRequests(),
    scheduleRules = schedule.slots.toScheduleRuleRequests(),
    // Nomis models the negative (exclude) and Activities models the positive (runs on)
    excludeBankHolidays = !schedule.runsOnBankHoliday,
    outsideWork = activity.outsideWork,
  )

  private fun toNomisActivityDescription(activityDescription: String): String = activityDescription.substring(0, min(40, activityDescription.length))

  private fun List<ActivityScheduleSlot>.toScheduleRuleRequests(): List<ScheduleRuleRequest> = map { slot ->
    ScheduleRuleRequest(
      startTime = slot.startTime,
      endTime = slot.endTime,
      monday = slot.mondayFlag,
      tuesday = slot.tuesdayFlag,
      wednesday = slot.wednesdayFlag,
      thursday = slot.thursdayFlag,
      friday = slot.fridayFlag,
      saturday = slot.saturdayFlag,
      sunday = slot.sundayFlag,
    )
  }

  private fun List<ActivityPay>.toPayRateRequests(): List<PayRateRequest> = map { p ->
    PayRateRequest(
      incentiveLevel = p.incentiveNomisCode,
      payBand = p.prisonPayBand.nomisPayBand.toString(),
      rate = BigDecimal(p.rate!!).movePointLeft(2),
      startDate = p.startDate,
    )
  }

  suspend fun createRetry(message: CreateMappingRetryMessage<ActivityMappingDto>) {
    mappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "activity-create-mapping-retry-success",
          mapOf("activityScheduleId" to message.mapping.activityScheduleId.toString()),
        )
      }
  }

  suspend fun deleteAllActivities() {
    mappingService.getAllMappings().forEach { mapping ->
      runCatching {
        activitiesNomisApiService.deleteActivity(mapping.nomisCourseActivityId)
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
  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class ScheduleDomainEvent(
  val eventType: String,
  val additionalInformation: ScheduleAdditionalInformation,
  val version: String,
  val description: String,
)

data class ScheduleAdditionalInformation(
  val activityScheduleId: Long,
)

fun ActivitySchedule.toCreatePayPerSession(): CreateActivityRequest.PayPerSession = CreateActivityRequest.PayPerSession.entries
  .first { it.value == this.activity.payPerSession.value }

fun ActivitySchedule.toUpdatePayPerSession(): UpdateActivityRequest.PayPerSession = UpdateActivityRequest.PayPerSession.entries
  .first { it.value == this.activity.payPerSession.value }
