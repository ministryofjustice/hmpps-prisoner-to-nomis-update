package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNotFound

@Service
class VisitsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: VisitMappingDto) {
    webClient.post()
      .uri("/mapping/visits")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenNomisIdOrNull(nomisId: Long): VisitMappingDto? =
    webClient.get()
      .uri("/mapping/visits/nomisId/{nomisId}", nomisId)
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun getMappingGivenVsipIdOrNull(vsipId: String): VisitMappingDto? =
    webClient.get()
      .uri("/mapping/visits/vsipId/{vsipId}", vsipId)
      .retrieve()
      .bodyToMono(VisitMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()

  suspend fun getMappingGivenVsipId(vsipId: String): VisitMappingDto =
    webClient.get()
      .uri("/mapping/visits/vsipId/{vsipId}", vsipId)
      .retrieve()
      .awaitBody()
}

data class VisitMappingDto(
  val nomisId: String,
  val vsipId: String,
  val label: String? = null,
  val mappingType: String,
)
