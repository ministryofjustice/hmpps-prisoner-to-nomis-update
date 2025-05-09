package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto

@Service
class OrganisationsMappingApiService(@Qualifier("mappingWebClient") val webClient: WebClient) {
  suspend fun getByDpsOrganisationIdOrNull(organisationId: String): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/organisation/dps-organisation-id/{organisationId}",
      organisationId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun deleteByDpsOrganisationId(dpsOrganisationId: String) {
    webClient.delete()
      .uri("/mapping/corporate/organisation/dps-organisation-id/{dpsOrganisationId}", dpsOrganisationId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
