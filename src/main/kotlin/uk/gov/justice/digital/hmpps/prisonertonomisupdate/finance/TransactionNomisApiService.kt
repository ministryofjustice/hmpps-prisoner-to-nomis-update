package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@Service
class TransactionNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "TransactionNomisApiService"),
  )
  suspend fun getFirstTransactionIdFrom(fromDate: LocalDate): Long = webClient
    .get()
    .uri("/transactions/{date}/first", fromDate)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getTransactions(lastTransactionId: Long = 0, transactionEntrySequence: Int = 1, pageSize: Int = 20): List<OffenderTransactionDto> = webClient.get()
    .uri("/transactions/from/{transactionId}/{transactionEntrySequence}?pageSize={pageSize}", lastTransactionId, transactionEntrySequence, pageSize)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  // Not sure which we need for the ones below (ones above, at least, will be needed for reconciliation)
  suspend fun getTransactions(transactionId: Long): List<OffenderTransactionDto> = webClient
    .get()
    .uri("/transactions/{transactionId}", transactionId)
    .retrieve()
    .awaitBody()

  suspend fun getGLTransactions(transactionId: Long): List<GeneralLedgerTransactionDto> = webClient
    .get()
    .uri("/transactions/{transactionId}/general-ledger", transactionId)
    .retrieve()
    .awaitBody()

  suspend fun getGLTransactionsForPrisoner(offenderNo: String): PrisonerTransactionLists = webClient
    .get()
    .uri("/transactions/prisoners/{offenderNo}", offenderNo)
    .retrieve()
    .awaitBodyWithRetry(retrySpec = backoffSpec)

  suspend fun getGLTransactionsForRange(
    transactionId: Long,
    transactionEntrySequence: Int,
    generalLedgerEntrySequence: Int,
    pageSize: Int,
  ): List<GeneralLedgerTransactionDto> = webClient
    .get()
    .uri("/transactions/from/{transactionId}/{transactionEntrySequence}/{generalLedgerEntrySequence}?pageSize=$pageSize", transactionId)
    .retrieve()
    .awaitBodyWithRetry(retrySpec = backoffSpec)
}

data class PrisonerTransactionLists(
  val offenderTransactions: List<OffenderTransactionDto>,
  val orphanGlTransactions: List<GeneralLedgerTransactionDto>,
)
