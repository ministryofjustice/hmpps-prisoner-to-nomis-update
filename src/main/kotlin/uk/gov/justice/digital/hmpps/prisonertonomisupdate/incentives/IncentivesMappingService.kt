package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Service
class IncentivesMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: IncentiveMappingDto) {
    webClient.post()
      .uri("/mapping/incentives")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getMappingGivenIncentiveId(incentiveId: Long): IncentiveMappingDto? =
    webClient.get()
      .uri("/mapping/incentives/incentive-id/$incentiveId")
      .retrieve()
      .bodyToMono(IncentiveMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
}

data class IncentiveMappingDto(
  val nomisBookingId: Long,
  val nomisIncentiveSequence: Int,
  val incentiveId: Long,
  val label: String? = null,
  val mappingType: String,
  val whenCreated: LocalDateTime? = null,
)
