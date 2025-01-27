package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import java.time.LocalDateTime

@Service
class ActivitiesMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: ActivityMappingDto) {
    webClient.post()
      .uri("/mapping/activities")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun updateMapping(request: ActivityMappingDto) {
    webClient.put()
      .uri("/mapping/activities")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingsOrNull(activityScheduleId: Long): ActivityMappingDto? = webClient.get()
    .uri("/mapping/activities/activity-schedule-id/{activityScheduleId}", activityScheduleId)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappings(activityScheduleId: Long): ActivityMappingDto = webClient.get()
    .uri("/mapping/activities/activity-schedule-id/{activityScheduleId}", activityScheduleId)
    .retrieve()
    .awaitBody()

  suspend fun getScheduledInstanceMappingOrNull(scheduledInstanceId: Long): ActivityScheduleMappingDto? = webClient.get()
    .uri("/mapping/activities/schedules/scheduled-instance-id/{scheduledInstanceId}", scheduledInstanceId)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getAllMappings(): List<ActivityMappingDto> = webClient.get()
    .uri("/mapping/activities")
    .retrieve()
    .awaitBody()

  suspend fun deleteMapping(activityScheduleId: Long) {
    webClient.delete()
      .uri("/mapping/activities/activity-schedule-id/{activityScheduleId}", activityScheduleId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteMappingsGreaterThan(maxNomisCourseScheduleId: Long) {
    webClient.delete()
      .uri("/mapping/schedules/max-nomis-schedule-id/{maxCourseScheduleId}", maxNomisCourseScheduleId)
      .retrieve()
      .awaitBodilessEntity()
  }
}

data class ActivityMappingDto(
  val nomisCourseActivityId: Long,
  val activityScheduleId: Long,
  val activityId: Long?,
  val mappingType: String,
  val scheduledInstanceMappings: List<ActivityScheduleMappingDto> = listOf(),
  val whenCreated: LocalDateTime? = null,
)

data class ActivityScheduleMappingDto(
  val scheduledInstanceId: Long,
  val nomisCourseScheduleId: Long,
  val mappingType: String,
  val whenCreated: LocalDateTime? = null,
)
