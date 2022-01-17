package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  fun createVisit(request: CreateVisitDto): CreateVisitResponseDto {
    return webClient.post()
      .uri("/visits")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(CreateVisitResponseDto::class.java)
      .block()!!
  }
}

data class CreateVisitDto(
  val offenderNo: String
)

data class CreateVisitResponseDto(
  val visitId: String
)
