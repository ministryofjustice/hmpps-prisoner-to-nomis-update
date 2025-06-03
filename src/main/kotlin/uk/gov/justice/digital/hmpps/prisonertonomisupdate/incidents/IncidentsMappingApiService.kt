package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncidentMappingDto

@Service
class IncidentsMappingApiService(@Qualifier("mappingWebClient") val webClient: WebClient) {
  suspend fun getByDpsIncidentIdOrNull(dpsIncidentId: Long): IncidentMappingDto? = webClient.get()
    .uri(
      "/mapping/incidents/dps-incident-id/{dpsIncidentId}",
      dpsIncidentId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsIncidentId(dpsIncidentId: Long): IncidentMappingDto = webClient.get()
    .uri(
      "/mapping/incidents/dps-incident-id/{dpsIncidentId}",
      dpsIncidentId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createIncidentMapping(mappings: IncidentMappingDto) = webClient.post()
    .uri("/mapping/incidents")
    .bodyValue(mappings)
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteByDpsIncidentId(dpsIncidentId: Long) {
    webClient.delete()
      .uri(
        "/mapping/incidents/dps-incident-id/{dpsIncidentId}",
        dpsIncidentId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }
}
