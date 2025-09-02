package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.UUID

@Service
class FinanceApiService(
  @Qualifier("financeApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "FinanceApiService"),
  )

  suspend fun getPrisonerTransactions(offenderNo: String): List<DpsTransaction> = webClient
    .get()
    .uri("/TBC/{offenderNo}", offenderNo)
    .retrieve()
    .awaitBodyWithRetry(retrySpec = backoffSpec)
}

data class DpsTransaction(
  val transactionId: UUID,
  val legacyTransactionId: Long,
)
