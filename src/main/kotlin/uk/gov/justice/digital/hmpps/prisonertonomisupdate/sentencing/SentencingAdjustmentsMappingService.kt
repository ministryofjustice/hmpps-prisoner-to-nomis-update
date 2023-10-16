package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNotFound
import java.time.LocalDateTime

@Service
class SentencingAdjustmentsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: SentencingAdjustmentMappingDto) {
    webClient.post()
      .uri("/mapping/sentencing/adjustments")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenAdjustmentIdOrNull(adjustmentId: String): SentencingAdjustmentMappingDto? =
    webClient.get()
      .uri("/mapping/sentencing/adjustments/adjustment-id/{adjustmentId}", adjustmentId)
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun getMappingGivenAdjustmentId(adjustmentId: String): SentencingAdjustmentMappingDto =
    webClient.get()
      .uri("/mapping/sentencing/adjustments/adjustment-id/{adjustmentId}", adjustmentId)
      .retrieve()
      .awaitBody()

  suspend fun deleteMappingGivenAdjustmentId(adjustmentId: String): Unit =
    webClient.delete()
      .uri("/mapping/sentencing/adjustments/adjustment-id/{adjustmentId}", adjustmentId)
      .retrieve()
      .awaitBody()
}

data class SentencingAdjustmentMappingDto(
  val nomisAdjustmentId: Long,
  val nomisAdjustmentCategory: String,
  val adjustmentId: String,
  val label: String? = null,
  val mappingType: String = "SENTENCING_CREATED",
  val whenCreated: LocalDateTime? = null,
)
