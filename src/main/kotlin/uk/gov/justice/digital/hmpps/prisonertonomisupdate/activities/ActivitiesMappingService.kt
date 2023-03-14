package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNotFound
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
      .awaitBodilessEntity()
  }

  suspend fun getMappingGivenActivityScheduleIdOrNull(id: Long): ActivityMappingDto? =
    webClient.get()
      .uri("/mapping/activities/activity-schedule-id/$id")
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun getMappingGivenActivityScheduleId(id: Long): ActivityMappingDto =
    webClient.get()
      .uri("/mapping/activities/activity-schedule-id/$id")
      .retrieve()
      .awaitBody()
}

data class ActivityMappingDto(
  val nomisCourseActivityId: Long,
  val activityScheduleId: Long,
  val mappingType: String,
  val whenCreated: LocalDateTime? = null,
)
