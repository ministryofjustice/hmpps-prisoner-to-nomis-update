package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourseScheduleRequest

@Service
class SchedulesService(
  private val activitiesApiService: ActivitiesApiService,
  private val activitiesNomisApiService: ActivitiesNomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun updateScheduledInstance(amendInstanceEvent: ScheduledInstanceDomainEvent) {
    val scheduledInstanceId = amendInstanceEvent.additionalInformation.scheduledInstanceId
    val telemetryMap = mutableMapOf(
      "dpsScheduledInstanceId" to scheduledInstanceId.toString(),
    )

    runCatching {
      val scheduledInstance = activitiesApiService.getScheduledInstance(scheduledInstanceId)
        .also {
          telemetryMap["dpsActivityScheduleId"] = it.activitySchedule.id.toString()
          telemetryMap["scheduleDate"] = it.date.toString()
          telemetryMap["startTime"] = it.startTime
          telemetryMap["endTime"] = it.endTime
          telemetryMap["prisonId"] = it.activitySchedule.activity.prisonCode
        }

      val mappings = mappingService.getMappings(scheduledInstance.activitySchedule.id)
      val nomisCourseActivityId = mappings.nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }
      val nomisCourseScheduleId = mappings.scheduledInstanceMappings.find { it.scheduledInstanceId == scheduledInstanceId }
        ?.nomisCourseScheduleId
        ?.also { telemetryMap["nomisCourseScheduleId"] = it.toString() }
        ?: throw ValidationException("Mapping for Activity's scheduled instance id not found: $scheduledInstanceId")

      scheduledInstance.toCourseScheduleRequest(nomisCourseScheduleId)
        .let { activitiesNomisApiService.updateScheduledInstance(nomisCourseActivityId, it) }
    }.onSuccess {
      telemetryClient.trackEvent("activity-scheduled-instance-amend-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-scheduled-instance-amend-failed", telemetryMap, null)
      throw e
    }
  }

  /*
   * There is a problem in preprod where the activity mappings are refreshed from prod later than NOMIS is refreshed.
   * This results in mappings existing for course schedules that don't actually exist in NOMIS preprod.
   * New course schedules are then prevented from being synchronised to NOMIS because the mapping already exists.
   *
   * When run after a preprod refresh of the mappings DB, this endpoint will remove any mappings that don't exist in NOMIS.
   */
  suspend fun deleteUnknownMappings() = activitiesNomisApiService.getMaxCourseScheduleId().apply {
    mappingService.deleteMappingsGreaterThan(this)
  }
}

fun List<ScheduledInstance>.toCourseScheduleRequests(mappings: List<ActivityScheduleMappingDto>? = null) = map {
  CourseScheduleRequest(
    id = mappings?.find { mapping -> mapping.scheduledInstanceId == it.id }?.nomisCourseScheduleId,
    date = it.date,
    startTime = it.startTime,
    endTime = it.endTime,
    cancelled = it.cancelled,
  )
}

fun ActivityScheduleInstance.toCourseScheduleRequest(nomisCourseScheduleId: Long) = CourseScheduleRequest(
  id = nomisCourseScheduleId,
  date = date,
  startTime = startTime,
  endTime = endTime,
  cancelled = cancelled,
)

data class ScheduledInstanceDomainEvent(
  val eventType: String,
  val additionalInformation: ScheduledInstanceAdditionalInformation,
  val version: String,
  val description: String,
)

data class ScheduledInstanceAdditionalInformation(
  val scheduledInstanceId: Long,
)
