package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.ReconciliationResponse

@Service
class TransferSchedulerDpsApiService(
  @Qualifier("transferSchedulerApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "TransferSchedulerDpsApiService"),
  )

  private val syncApi = SyncApi(webClient)

  suspend fun getTransferSchedulerReconciliation(personIdentifier: String): ReconciliationResponse = syncApi.prepare(syncApi.reconcileRequestConfig(personIdentifier))
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)
}
