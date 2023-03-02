package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Service
class ActivitiesMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  fun createMapping(request: ActivityMappingDto) {
    webClient.post()
      .uri("/mapping/activities")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .block()
  }

  fun getMappingGivenActivityScheduleIdOrNull(id: Long): ActivityMappingDto? =
    webClient.get()
      .uri("/mapping/activities/activity-schedule-id/$id")
      .retrieve()
      .bodyToMono(ActivityMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .block()

  fun getMappingGivenActivityScheduleId(id: Long): ActivityMappingDto =
    webClient.get()
      .uri("/mapping/activities/activity-schedule-id/$id")
      .retrieve()
      .bodyToMono(ActivityMappingDto::class.java).block()!!
}

data class ActivityMappingDto(
  val nomisCourseActivityId: Long,
  val activityScheduleId: Long,
  val mappingType: String,
  val whenCreated: LocalDateTime? = null,
)
