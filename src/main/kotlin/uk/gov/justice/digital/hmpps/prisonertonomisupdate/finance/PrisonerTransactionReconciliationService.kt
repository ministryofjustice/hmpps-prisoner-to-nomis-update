package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class PrisonerTransactionReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: TransactionNomisApiService,
  private val mappingApiService: TransactionMappingApiService,
  private val dpsApiService: FinanceDpsApiService,
  @param:Value($$"${reports.prisoner-transactions.reconciliation.page-size:20}") private val pageSize: Int = 20,
) {
  private companion object {
    private const val TELEMETRY_PRISONER_PREFIX = "prisoner-transactions-reconciliation"
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReportBatch(entryDate: LocalDate = LocalDate.now()) {
    telemetryClient.trackEvent("$TELEMETRY_PRISONER_PREFIX-requested", mapOf())

    runCatching { generateReconciliationReport(entryDate) }
      .onSuccess {
        log.info("Prisoner transactions reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-report",
          mapOf(
            "transaction-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_PRISONER_PREFIX-report", mapOf("success" to "false"))
        log.error("Prisoner transactions reconciliation report failed", it)
      }
  }

  private fun List<MismatchPrisonerTransaction>.asMap(): Pair<String, String> = sortedBy { it.nomisTransactionId }.take(10)
    .let { mismatch -> "nomisTransactionIds" to mismatch.map { it.nomisTransactionId }.joinToString() }

  suspend fun generateReconciliationReport(entryDate: LocalDate): ReconciliationResult<MismatchPrisonerTransaction> = generateReconciliationReport(
    threadCount = pageSize,
    checkMatch = ::checkTransactionMatch,
    nextPage = { last ->
      getNextNomisPrisonerTransactionIdsForPage(last, entryDate)
    },
  )

  private suspend fun getNextNomisPrisonerTransactionIdsForPage(lastNomisPrisonerTransactionId: Long, entryDate: LocalDate): ReconciliationPageResult<Long> = runCatching {
    nomisApiService.getPrisonerTransactionIdsByLastId(
      lastPrisonerTransactionId = lastNomisPrisonerTransactionId,
      entryDate,
      pageSize = pageSize,
    )
  }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch-page-error",
        mapOf(
          "nomisPrisonerTransactionId" to lastNomisPrisonerTransactionId.toString(),
        ),
      )
      log.error("Unable to match entire page of prisoner transactions from nomisPrisonerTransactionId: $lastNomisPrisonerTransactionId", it)
    }
    .map { page ->
      ReconciliationSuccessPageResult(
        ids = page.ids.map { it.transactionId },
        last = page.ids.last().transactionId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from prisonerTransaction: $lastNomisPrisonerTransactionId, with $pageSize prisoner transactions") }

  suspend fun checkTransactionMatch(nomisTransactionId: Long): MismatchPrisonerTransaction? = runCatching {
    val (nomisTransaction, dpsTransactionResult: DpsTransactionResult) = withContext(Dispatchers.Unconfined) {
      async {
        nomisApiService.getPrisonerTransaction(nomisTransactionId)
      } to
        async {
          val mapping = mappingApiService.getByNomisTransactionIdOrNull(nomisTransactionId)
          if (mapping != null) {
            val dpsTransaction = dpsApiService.getPrisonerTransactionOrNull(UUID.fromString(mapping.dpsTransactionId))
            if (dpsTransaction == null) {
              NoDpsTransaction(mapping.dpsTransactionId)
            } else {
              Transaction(dpsTransaction)
            }
          } else {
            NoMapping()
          }
        }
    }.awaitBoth()

    val prisonNumber = nomisTransaction.first().offenderNo

    return when (dpsTransactionResult) {
      is NoMapping -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          mapOf(
            "nomisTransactionId" to nomisTransactionId.toString(),
            "prisonNumber" to prisonNumber,
            "reason" to "transaction-mapping-missing",
          ),
        )
        MismatchPrisonerTransaction(nomisTransactionId = nomisTransactionId, prisonNumber = prisonNumber, reason = "transaction-mapping-missing")
      }

      is NoDpsTransaction -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          mapOf(
            "nomisTransactionId" to nomisTransactionId.toString(),
            "dpsTransactionId" to dpsTransactionResult.transactionId,
            "prisonNumber" to prisonNumber,
            "reason" to "dps-transaction-missing",
          ),
        )
        MismatchPrisonerTransaction(nomisTransactionId = nomisTransactionId, dpsTransactionId = dpsTransactionResult.transactionId, prisonNumber = prisonNumber, reason = "dps-transaction-missing")
      }

      is Transaction -> {
        val dpsTransaction = dpsTransactionResult.transaction
        if (nomisTransaction.toPrisonerTransactionSummary() != dpsTransaction.toPrisonerTransactionSummary()) {
          log.info(
            "Mismatch found for transaction: {} {}",
            nomisTransaction.toPrisonerTransactionSummary(),
            dpsTransaction.toPrisonerTransactionSummary(),
          )
          telemetryClient.trackEvent(
            "$TELEMETRY_PRISONER_PREFIX-mismatch",
            mapOf(
              "nomisTransactionId" to nomisTransactionId.toString(),
              "dpsTransactionId" to dpsTransaction.synchronizedTransactionId.toString(),
              "prisonNumber" to nomisTransaction.first().offenderNo,
              "reason" to "transaction-different-details",
            ),
          )
          MismatchPrisonerTransaction(
            nomisTransactionId = nomisTransactionId,
            dpsTransactionId = dpsTransaction.synchronizedTransactionId.toString(),
            prisonNumber = prisonNumber,
            reason = "transaction-different-details",
            nomisTransaction = nomisTransaction.toPrisonerTransactionSummary(),
            dpsTransaction = dpsTransaction.toPrisonerTransactionSummary(),
          )
        } else {
          null
        }
      }
    }
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-mismatch-error",
      mapOf(
        "nomisTransactionId" to "$nomisTransactionId",
      ),
    )
  }.getOrNull()
}

data class MismatchPrisonerTransaction(
  val nomisTransactionId: Long,
  val dpsTransactionId: String? = null,
  val prisonNumber: String,
  val reason: String,
  val nomisTransaction: PrisonerTransactionSummary? = null,
  val dpsTransaction: PrisonerTransactionSummary? = null,
)

data class PrisonerTransactionSummary(
  val prisonId: String,
  val nomisTransactionId: Long,
  val entryDateTime: LocalDateTime,
  val entries: List<PrisonerTransactionEntry>,
)

data class PrisonerTransactionEntry(
  val transactionSequence: Int,
  val subAccountType: String,
  val postingType: String,
  val amount: BigDecimal,
  val prisonEntries: List<TransactionEntry>,
)

fun List<OffenderTransactionDto>.toPrisonerTransactionSummary(): PrisonerTransactionSummary = first().run {
  PrisonerTransactionSummary(
    nomisTransactionId = transactionId,
    prisonId = caseloadId,
    entryDateTime = createdAt,
    entries = this@toPrisonerTransactionSummary.map { it.toPrisonerTransactionEntry() },
  )
}
fun OffenderTransactionDto.toPrisonerTransactionEntry() = PrisonerTransactionEntry(
  transactionSequence = transactionEntrySequence,
  subAccountType = subAccountType.value,
  postingType = postingType.value,
  amount = amount.setScale(2, RoundingMode.HALF_UP),
  prisonEntries = generalLedgerTransactions.map { it.toTransactionEntry() },
)
fun SyncOffenderTransactionResponse.toPrisonerTransactionSummary() = PrisonerTransactionSummary(
  nomisTransactionId = legacyTransactionId!!,
  prisonId = caseloadId,
  entryDateTime = transactionTimestamp,
  entries = transactions.map { it.toPrisonerTransactionEntry() },
)
fun OffenderTransaction.toPrisonerTransactionEntry() = PrisonerTransactionEntry(
  transactionSequence = entrySequence,
  subAccountType = subAccountType,
  postingType = postingType.value,
  amount = amount.setScale(2, RoundingMode.HALF_UP),
  prisonEntries = generalLedgerEntries.map { it.toTransactionEntry() },
)

sealed interface DpsTransactionResult
data class Transaction(val transaction: SyncOffenderTransactionResponse) : DpsTransactionResult
class NoMapping : DpsTransactionResult
data class NoDpsTransaction(val transactionId: String) : DpsTransactionResult
