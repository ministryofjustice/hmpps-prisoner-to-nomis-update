package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Service
class SentencingAdjustmentsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient
) {

  suspend fun createMapping(request: SentencingAdjustmentMappingDto) {
    webClient.post()
      .uri("/mapping/sentencing/adjustments")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .awaitSingleOrNull()
  }

  suspend fun getMappingGivenAdjustmentId(adjustmentId: String): SentencingAdjustmentMappingDto? =
    webClient.get()
      .uri("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId")
      .retrieve()
      .bodyToMono(SentencingAdjustmentMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()

  suspend fun deleteMappingGivenAdjustmentId(adjustmentId: String): Unit =
    webClient.delete()
      .uri("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId")
      .retrieve()
      .awaitBody()
}

data class SentencingAdjustmentMappingDto(
  val nomisAdjustmentId: Long,
  val nomisAdjustmentCategory: String,
  val adjustmentId: String,
  val label: String? = null,
  val mappingType: String = "SENTENCING_CREATED",
  val whenCreated: LocalDateTime? = null
)
