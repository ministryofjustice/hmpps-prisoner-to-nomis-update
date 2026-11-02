package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.ReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class PersonLocationDpsApiService(
  @Qualifier("personLocationDpsApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "PersonLocationDpsApiService"),
  )

  private val syncApi = SyncApi(webClient)

  suspend fun getReconciliation(personIdentifier: String): ReconciliationResponse = syncApi.prepare(syncApi.reconcileRequestConfig(personIdentifier))
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)
}
