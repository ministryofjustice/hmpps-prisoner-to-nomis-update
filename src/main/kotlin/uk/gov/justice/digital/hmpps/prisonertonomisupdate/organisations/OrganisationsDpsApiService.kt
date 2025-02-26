package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class OrganisationsDpsApiService(
  @Qualifier("organisationsApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "OrganisationsDpsApiService"),
  )

  suspend fun getOrganisation(organisationId: Long): OrganisationDetails? = webClient.get()
    .uri("/organisation/{organisationId}", organisationId)
    .retrieve()
    .awaitBodyOrNullForNotFound(backoffSpec)
}
