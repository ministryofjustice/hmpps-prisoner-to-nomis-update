package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationMappingDto

@Service
class AdjudicationsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: AdjudicationMappingDto) {
    webClient.post()
      .uri("/mapping/adjudications")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenChargeNumberOrNull(chargeNumber: String): AdjudicationMappingDto? =
    webClient.get()
      .uri("/mapping/adjudications/charge-number/$chargeNumber")
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun getMappingGivenChargeNumber(chargeNumber: String): AdjudicationMappingDto =
    webClient.get()
      .uri("/mapping/adjudications/charge-number/$chargeNumber")
      .retrieve()
      .awaitBody()

  suspend fun deleteMappingGivenChargeNumber(chargeNumber: String): Unit =
    webClient.delete()
      .uri("/mapping/adjudications/charge-number/$chargeNumber")
      .retrieve()
      .awaitBody()
}
