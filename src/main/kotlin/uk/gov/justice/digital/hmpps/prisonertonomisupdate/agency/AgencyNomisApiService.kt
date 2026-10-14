package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.AgencyResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyEmailAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyPhoneNumberRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyEmailAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumbersRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class AgencyNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val retrySpec = retryApiService.getBackoffSpec()
    .withRetryContext(
      Context.of("api", "AgencyNomisApiService"),
    )

  private val api = AgencyResourceApi(webClient)

  suspend fun getAgency(agencyId: String): AgencyResponse? = api.prepare(api.getAgencyRequestConfig(agencyId)).retrieve().awaitBodyOrNullForNotFound(retrySpec)
  suspend fun getAgencyIds(): AgencyIdsResponse = api.getAllAgencies(excludeType = listOf("INST")).retryWhen(retrySpec).awaitSingle()
  suspend fun createAgency(request: CreateAgencyRequest) = api.createAgency(request).awaitSingle()
  suspend fun createAgencyEmail(agencyId: String, request: CreateAgencyEmailAddressRequest) = api.createAgencyEmail(agencyId, request).awaitSingle()
  suspend fun refreshAgencyEmails(agencyId: String, request: UpdateAgencyEmailAddressesRequest) = api.updateAgencyEmailAddresses(agencyId, request).awaitSingle()
  suspend fun createAgencyPhone(agencyId: String, request: CreateAgencyPhoneNumberRequest) = api.createAgencyPhone(agencyId, request).awaitSingle()
  suspend fun refreshAgencyPhones(agencyId: String, request: UpdateAgencyPhoneNumbersRequest) = api.updateAgencyPhoneNumbers(agencyId, request).awaitSingle()
  suspend fun createAgencyAddress(agencyId: String, request: CreateAgencyAddressRequest) = api.createAgencyAddress(agencyId, request).awaitSingle()
  suspend fun refreshAgencyAddresses(agencyId: String, request: UpdateAgencyAddressesRequest) = api.updateAgencyAddresses(agencyId, request).awaitSingle()
}
