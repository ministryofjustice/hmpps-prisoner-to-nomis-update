package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourseScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime

@Service
class SchedulesService(
  private val activitiesApiService: ActivitiesApiService,
  private val nomisApiService: NomisApiService,
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
        }

      val nomisCourseActivityId = mappingService.getMappings(scheduledInstance.activitySchedule.id).nomisCourseActivityId
        .also { telemetryMap["nomisCourseActivityId"] = it.toString() }

      scheduledInstance.toCourseScheduleRequest()
        .let { nomisApiService.updateScheduledInstance(nomisCourseActivityId, it) }
        .also { telemetryMap["nomisCourseScheduleId"] = it.courseScheduleId.toString() }
    }.onSuccess {
      telemetryClient.trackEvent("activity-scheduled-instance-amend-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("activity-scheduled-instance-amend-failed", telemetryMap, null)
      throw e
    }
  }
}

fun List<ScheduledInstance>.toCourseScheduleRequests() =
  map {
    CourseScheduleRequest(
      date = it.date,
      startTime = it.startTime,
      endTime = it.endTime,
      cancelled = it.cancelled,
    )
  }

fun ActivityScheduleInstance.toCourseScheduleRequest() =
  CourseScheduleRequest(
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
  val occurredAt: LocalDateTime,
)

data class ScheduledInstanceAdditionalInformation(
  val activityScheduleId: Long,
  val scheduledInstanceId: Long,
)
