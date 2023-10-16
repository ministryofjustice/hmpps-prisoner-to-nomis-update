package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AdjudicationPunishmentBatchMappingDto

@Service
class PunishmentsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: AdjudicationPunishmentBatchMappingDto) {
    webClient.post()
      .uri("/mapping/punishments")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }
}
