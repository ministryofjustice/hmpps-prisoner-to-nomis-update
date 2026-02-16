package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.SentencingMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentencingAdjustmentMappingDto

@Service
class SentencingAdjustmentsMappingService(
  @Qualifier("mappingWebClient") webClient: WebClient,
) {
  private val sentencingMappingResourceApi = SentencingMappingResourceApi(webClient)

  suspend fun createMapping(request: SentencingAdjustmentMappingDto) {
    sentencingMappingResourceApi.prepare(
      sentencingMappingResourceApi.createMapping4RequestConfig(request),
    )
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenAdjustmentIdOrNull(adjustmentId: String): SentencingAdjustmentMappingDto? = sentencingMappingResourceApi.prepare(
    sentencingMappingResourceApi.getSentencingAdjustmentMappingRequestConfig(adjustmentId),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenAdjustmentId(adjustmentId: String): SentencingAdjustmentMappingDto = sentencingMappingResourceApi
    .getSentencingAdjustmentMapping(adjustmentId).awaitSingle()

  suspend fun deleteMappingGivenAdjustmentId(adjustmentId: String): Unit = sentencingMappingResourceApi
    .deleteSentenceAdjustmentMapping(adjustmentId).awaitSingle()
}
