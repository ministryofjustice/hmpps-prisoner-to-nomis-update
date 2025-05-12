package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.PagedModelSyncOrganisationId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncAddressPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncTypesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncWebResponse
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

  suspend fun getOrganisationIds(
    pageNumber: Long = 0,
    pageSize: Long = 1,
  ): PagedModelSyncOrganisationId = webClient.get()
    .uri {
      it.path("/sync/organisations/reconcile")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getSyncOrganisation(organisationId: Long): SyncOrganisationResponse = webClient.get()
    .uri("/sync/organisation/{organisationId}", organisationId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getSyncOrganisationWeb(organisationWebId: Long): SyncWebResponse = webClient.get()
    .uri("/sync/organisation-web/{organisationWebId}", organisationWebId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getSyncOrganisationTypes(organisationId: Long): SyncTypesResponse = webClient.get()
    .uri("/sync/organisation-types/{organisationId}", organisationId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getSyncOrganisationPhone(organisationPhoneId: Long): SyncPhoneResponse = webClient.get()
    .uri("/sync/organisation-phone/{organisationPhoneId}", organisationPhoneId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getSyncOrganisationEmail(organisationEmailId: Long): SyncEmailResponse = webClient.get()
    .uri("/sync/organisation-email/{organisationEmailId}", organisationEmailId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getSyncOrganisationAddress(organisationAddressId: Long): SyncAddressResponse = webClient.get()
    .uri("/sync/organisation-address/{organisationAddressId}", organisationAddressId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getSyncOrganisationAddressPhone(organisationAddressPhoneId: Long): SyncAddressPhoneResponse = webClient.get()
    .uri("/sync/organisation-address-phone/{organisationAddressPhoneId}", organisationAddressPhoneId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)
}
