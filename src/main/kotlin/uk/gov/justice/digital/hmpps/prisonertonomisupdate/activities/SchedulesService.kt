package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateScheduleRequest
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class SchedulesService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: ActivitiesMappingService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun updateScheduleInstances(amendInstancesEvent: ScheduleDomainEvent) {
    val telemetryMap = mutableMapOf("activityScheduleId" to amendInstancesEvent.additionalInformation.activityScheduleId.toString())

    runCatching {
      val activitySchedule = activitiesApiService.getActivitySchedule(amendInstancesEvent.additionalInformation.activityScheduleId)

      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(activitySchedule.id).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      activitySchedule.instances.toScheduleRequests()
        .also { nomisApiService.updateScheduleInstances(nomisCourseActivityId, it) }
    }.onSuccess {
      telemetryClient.trackEvent("schedule-instances-amend-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("schedule-instances-amend-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun updateScheduledInstance(amendInstanceEvent: ScheduledInstanceDomainEvent) {
    val scheduledInstanceId = amendInstanceEvent.additionalInformation.scheduledInstanceId
    val telemetryMap = mutableMapOf(
      "scheduledInstanceId" to scheduledInstanceId.toString(),
    )

    runCatching {
      val scheduledInstance = activitiesApiService.getScheduledInstance(scheduledInstanceId)
        .also {
          telemetryMap["activityScheduleId"] = it.activitySchedule.id.toString()
          telemetryMap["scheduleDate"] = it.date.toString()
          telemetryMap["startTime"] = it.startTime
          telemetryMap["endTime"] = it.endTime
        }

      val nomisCourseActivityId = mappingService.getMappingGivenActivityScheduleId(scheduledInstance.activitySchedule.id).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      scheduledInstance.toUpdateScheduleRequest()
        .let { nomisApiService.updateScheduledInstance(nomisCourseActivityId, it) }
        .also { telemetryMap["nomisCourseScheduleId"] = it.courseScheduleId.toString() }
    }.onSuccess {
      telemetryClient.trackEvent("scheduled-instance-amend-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("scheduled-instance-amend-failed", telemetryMap, null)
      throw e
    }
  }
}

fun List<ScheduledInstance>.toScheduleRequests() =
  map {
    ScheduleRequest(
      date = it.date,
      startTime = LocalTime.parse(it.startTime),
      endTime = LocalTime.parse(it.endTime),
    )
  }

fun ActivityScheduleInstance.toUpdateScheduleRequest() =
  UpdateScheduleRequest(
    date = date,
    startTime = LocalTime.parse(startTime),
    endTime = LocalTime.parse(endTime),
    cancelled = cancelled,
  )

data class ScheduledInstanceDomainEvent(
  val eventType: String,
  val additionalInformation: ScheduledInstanceAdditionalInformation,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
)

data class ScheduledInstanceAdditionalInformation(
  val activityScheduleId: Long,
  val scheduledInstanceId: Long,
)
