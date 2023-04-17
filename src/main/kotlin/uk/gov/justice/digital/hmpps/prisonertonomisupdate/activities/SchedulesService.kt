package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ScheduleRequest
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
}

fun List<ScheduledInstance>.toScheduleRequests() =
  map {
    ScheduleRequest(
      date = it.date,
      startTime = LocalTime.parse(it.startTime),
      endTime = LocalTime.parse(it.endTime),
    )
  }
