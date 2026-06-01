package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.ReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.*

@Service
class CourtSchedulerDpsApiService(
  @Qualifier("courtSchedulerApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CourtSchedulerDpsApiService"),
  )

  private val syncApi = SyncApi(webClient)

  suspend fun getCourtSchedulerReconciliation(personIdentifier: String): ReconciliationResponse = syncApi.prepare(syncApi.getCourtAppearancesRequestConfig(personIdentifier))
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getCourtAppearance(id: UUID): CourtEvent = syncApi.getCourtAppearance1(id).awaitBodyOrLogAndRethrowBadRequest(backoffSpec)
}
