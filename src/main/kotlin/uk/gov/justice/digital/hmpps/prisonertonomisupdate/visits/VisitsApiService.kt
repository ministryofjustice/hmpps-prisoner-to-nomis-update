package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDateTime

@Service
class VisitsApiService(@Qualifier("visitsApiWebClient") private val webClient: WebClient) {

  suspend fun getVisit(visitId: String): VisitDto {
    return webClient.get()
      .uri("/visits/{visitId}", visitId)
      .retrieve()
      .awaitBody()
  }
}

data class VisitDto(
  val reference: String,
  val prisonerId: String,
  val visitors: List<Visitor> = listOf(),
  val prisonId: String,
  val visitType: String,
  val startTimestamp: LocalDateTime,
  val endTimestamp: LocalDateTime = LocalDateTime.now().plusHours(1),
  val visitStatus: String,
  val outcomeStatus: String? = null,
  val visitRoom: String,
  val visitRestriction: String,
) {
  data class Visitor(val nomisPersonId: Long)
}
