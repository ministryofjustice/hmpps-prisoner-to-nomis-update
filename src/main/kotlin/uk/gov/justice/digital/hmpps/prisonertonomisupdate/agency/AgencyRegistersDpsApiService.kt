package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.api.CourtResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agencyregisters.model.CourtDto
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

  private val api = CourtResourceApi(webClient)

  suspend fun getCourt(agencyId: String): CourtDto? = api.prepare(api.getCourtFromIdRequestConfig(agencyId)).retrieve().awaitBodyOrNullForNotFound(retrySpec)
}
