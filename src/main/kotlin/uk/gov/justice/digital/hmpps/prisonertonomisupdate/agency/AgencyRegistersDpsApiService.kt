package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.api.LegacySyncResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class AgencyRegistersDpsApiService(
  @Qualifier("agencyApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "AgencyRegistersDpsApiService"),
  )

  private val api = LegacySyncResourceApi(webClient)

  suspend fun getAgency(agencyId: String): LegacyAgencyDto? = api.prepare(api.getAgencyDetailsRequestConfig(agencyId)).retrieve().awaitBodyOrNullForNotFound(retrySpec)
  suspend fun getAgencyIds() = api.getAllAgencyIds().awaitSingle()
}
