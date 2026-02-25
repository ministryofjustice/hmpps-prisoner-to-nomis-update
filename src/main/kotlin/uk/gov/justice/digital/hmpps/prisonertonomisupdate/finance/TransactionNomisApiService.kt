package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PrisonTransactionsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PrisonerTransactionsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerTransactionIdsPage
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
  private val prisonTransactionsApi = PrisonTransactionsResourceApi(webClient)
  private val prisonerTransactionsApi = PrisonerTransactionsResourceApi(webClient)

  suspend fun getPrisonTransactions(prisonId: String, entryDate: LocalDate): List<GeneralLedgerTransactionDto> = prisonTransactionsApi
    .getTransactionsOn(prisonId, entryDate)
    .retryWhen(backoffSpec)
    .awaitSingle()

  // returns empty list if it does not exist
  suspend fun getPrisonTransaction(transactionId: Long): List<GeneralLedgerTransactionDto> = prisonTransactionsApi
    .getGLTransaction(transactionId)
    .awaitSingle()

  // returns empty list if it does not exist
  suspend fun getPrisonerTransaction(transactionId: Long): List<OffenderTransactionDto> = prisonerTransactionsApi
    .getTransaction(transactionId)
    .awaitSingle()

  suspend fun getPrisonerTransactions(prisonNumber: String): List<OffenderTransactionDto> = webClient.get()
    .uri("/transactions/prisoners/{prisonNumber}", prisonNumber)
    .retrieve()
    .awaitBodyWithRetry(retrySpec = backoffSpec)

  // TODO check if still needed
  suspend fun getTransactions(lastTransactionId: Long = 0, transactionEntrySequence: Int = 1, pageSize: Int = 20): List<OffenderTransactionDto> = webClient.get()
    .uri("/transactions/from/{transactionId}/{transactionEntrySequence}?pageSize={pageSize}", lastTransactionId, transactionEntrySequence, pageSize)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getFirstTransactionIdFrom(fromDate: LocalDate): Long = webClient
    .get()
    .uri("/transactions/{date}/first", fromDate)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getPrisonerTransactionIdsByLastId(
    lastPrisonerTransactionId: Long = 0,
    entryDate: LocalDate = LocalDate.now(),
    pageSize: Int,
  ): PrisonerTransactionIdsPage = prisonerTransactionsApi.findPrisonerTransactionsFromId(
    transactionId = lastPrisonerTransactionId,
    entryDate = entryDate,
    size = pageSize,
  ).retryWhen(backoffSpec).awaitSingle()
}

data class PrisonerTransactionLists(
  val offenderTransactions: List<OffenderTransactionDto>,
  val orphanGlTransactions: List<GeneralLedgerTransactionDto>,
)
