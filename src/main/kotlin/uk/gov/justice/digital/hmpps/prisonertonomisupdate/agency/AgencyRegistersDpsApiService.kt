package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.api.LegacySyncResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class AgencyRegistersDpsApiService(
  agencyRegistersApiWebClient: WebClient,
  retryApiService: RetryApiService,
) {

  private val legacySyncApi = LegacySyncResourceApi(agencyRegistersApiWebClient)

  suspend fun getAgency(agencyId: String): LegacyAgencyDto = legacySyncApi.getAgencyDetails(agencyId).awaitSingle()
}
