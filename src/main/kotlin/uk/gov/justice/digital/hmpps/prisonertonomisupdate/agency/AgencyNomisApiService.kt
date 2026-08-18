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
  suspend fun getAgencyIds(): AgencyIdsResponse = api.getAllAgencies(excludeType = listOf("INST")).awaitSingle()
}
