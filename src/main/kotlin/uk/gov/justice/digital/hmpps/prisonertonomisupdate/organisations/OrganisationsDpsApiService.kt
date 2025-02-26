package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationDetails

@Service
class OrganisationsDpsApiService(@Qualifier("organisationsApiWebClient") private val webClient: WebClient) {
  suspend fun getOrganisation(organisationId: Long): OrganisationDetails? = webClient.get()
    .uri("/organisation/{organisationId}", organisationId)
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
