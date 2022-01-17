package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.LocalTime

@Service
class VisitsApiService(@Qualifier("visitsApiWebClient") private val webClient: WebClient) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getVisit(visitId: String): VisitDto? {
    return webClient.get()
      .uri("/visits/$visitId")
      .retrieve()
      .bodyToMono(VisitDto::class.java)
      .block()
  }
}

data class VisitDto(
  val visitId: String,
  val prisonerId: String,
  val visitors: List<Visitor> = listOf(),
  val prisonId: String,
  val visitType: String,
  val visitRoom: String,
  val visitDate: LocalDate,
  val startTime: LocalTime,
  val endTime: LocalTime = LocalTime.now().plusHours(1),
  val currentStatus: String,
) {
  data class Visitor(val nomisPersonId: String)
}
