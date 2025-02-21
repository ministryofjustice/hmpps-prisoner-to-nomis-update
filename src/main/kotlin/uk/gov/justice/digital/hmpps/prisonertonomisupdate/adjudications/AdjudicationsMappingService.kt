package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationDeleteMappingDto
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

  suspend fun getMappingGivenChargeNumberOrNull(chargeNumber: String): AdjudicationMappingDto? = webClient.get()
    .uri("/mapping/adjudications/charge-number/{chargeNumber}", chargeNumber)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenChargeNumber(chargeNumber: String): AdjudicationMappingDto = webClient.get()
    .uri("/mapping/adjudications/charge-number/{chargeNumber}", chargeNumber)
    .retrieve()
    .awaitBody()

  suspend fun deleteMappingsForAdjudication(request: AdjudicationDeleteMappingDto) {
    webClient.post()
      .uri("/mapping/adjudications/delete-mappings")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
}
