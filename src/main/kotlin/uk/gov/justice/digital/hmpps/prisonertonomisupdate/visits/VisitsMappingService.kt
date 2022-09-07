package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Service
class VisitsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createMapping(request: VisitMappingDto) {
    webClient.post()
      .uri("/mapping")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .block()
  }

  fun getMappingGivenNomisId(nomisId: Long): VisitMappingDto? =
    webClient.get()
      .uri("/mapping/nomisId/$nomisId")
      .retrieve()
      .bodyToMono(VisitMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        log.debug("getMappingGivenNomisId not found for nomisId $nomisId with error response ${it.responseBodyAsString}")
        Mono.empty()
      }
      .block()

  fun getMappingGivenVsipId(vsipId: String): VisitMappingDto? =
    webClient.get()
      .uri("/mapping/vsipId/$vsipId")
      .retrieve()
      .bodyToMono(VisitMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .block()
}

data class VisitMappingDto(
  val nomisId: String,
  val vsipId: String,
  val label: String? = null,
  val mappingType: String,
)
