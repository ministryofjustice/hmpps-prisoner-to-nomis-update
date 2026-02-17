package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.String
import kotlin.collections.chunked
import kotlin.collections.flatten
import kotlin.collections.map

@Service
class PrisonTransactionReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: FinanceDpsApiService,
  private val nomisApiService: NomisApiService,
  private val transactionNomisApiService: TransactionNomisApiService,
  private val mappingService: TransactionMappingApiService,
  @Value($$"${reports.prisontransaction.reconciliation.page-size:10}")
  private val pageSize: Int = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(date: LocalDate? = null) {
    val prisonIds = nomisApiService.getActivePrisons().map { it.id }

    telemetryClient.trackEvent(
      "prison-transaction-reports-reconciliation-requested",
      mapOf("prisons" to prisonIds.size.toString()),
    )
    log.info("Prison transaction reconciliation report requested for ${prisonIds.size} prisons")

    val entryDate = date ?: LocalDate.now().minusDays(1)
    runCatching { generateReconciliationReport(prisonIds, entryDate) }
      .onSuccess {
        log.info("Prison transaction reconciliation report completed with ${it.size} mismatches")
        telemetryClient.trackEvent(
          "prison-transaction-reports-reconciliation-report",
          mapOf(
            "mismatch-count" to it.size.toString(),
            "success" to "true",
          ),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("prison-transaction-reports-reconciliation-report", mapOf("success" to "false"))
        log.error("Prison transaction reconciliation report failed", it)
      }
  }

  suspend fun generateReconciliationReport(prisonIds: List<String>, date: LocalDate): List<MismatchPrisonTransaction> = prisonIds.chunked(pageSize).flatMap { pagedPrisonIds ->
    withContext(Dispatchers.Unconfined) {
      pagedPrisonIds.map { async { checkTransactionsMatch(it, date) } }
    }.awaitAll().flatten()
  }

  suspend fun checkTransactionsMatch(prisonId: String, date: LocalDate): List<MismatchPrisonTransaction> {
    val nomisTransactionsForTheDay = doApiCallWithRetries {
      transactionNomisApiService.getPrisonTransactions(prisonId, date)
    }
    return nomisTransactionsForTheDay.groupBy { it.transactionId }.values.mapNotNull { checkTransactionMatch(it) }
  }

  suspend fun checkTransactionMatch(nomisTransactionId: Long): MismatchPrisonTransaction? = checkTransactionMatch(transactionNomisApiService.getPrisonTransaction(nomisTransactionId))

  suspend fun checkTransactionMatch(nomis: List<GeneralLedgerTransactionDto>): MismatchPrisonTransaction? = runCatching {
    val nomisTransaction = nomis.toTransactionSummary()
    val nomisTransactionId = nomisTransaction.nomisTransactionId
    mappingService.getByNomisTransactionIdOrNull(nomisTransactionId)?.let { mappingDto ->
      val dpsTransaction =
        doApiCallWithRetries {
          dpsApiService.getPrisonTransaction(UUID.fromString(mappingDto.dpsTransactionId)).toTransactionSummary()
        }

      val nomisTransactionEntryCount = nomisTransaction.entries.size
      val dpsTransactionEntryCount = dpsTransaction.entries.size
      val prisonId = nomisTransaction.prisonId

      val telemetry = mutableMapOf(
        "prisonId" to prisonId,
        "nomisTransactionId" to nomisTransactionId,
        "dpsTransactionId" to mappingDto.dpsTransactionId,
        "nomisTransactionEntryCount" to nomisTransactionEntryCount,
        "dpsTransactionEntryCount" to dpsTransactionEntryCount,
      )

      val differences = mutableMapOf<String, String>()

      appendDifference(
        nomisTransactionEntryCount.toString(),
        dpsTransactionEntryCount.toString(),
        differences,
        "transactionEntryCount",
      )

      if (differences.isEmpty()) {
        val (missingFromNomis, missingFromDps) =
          findMissingTransactionEntries(nomisTransaction.entries, dpsTransaction.entries)
        if (missingFromNomis.isNotEmpty() || missingFromDps.isNotEmpty()) {
          differences["entries"] = "nomisOnly=$missingFromDps, dpsOnly=$missingFromNomis"
        }
      }
      appendDifference(nomisTransaction.prisonId, dpsTransaction.prisonId, differences, "prisonId")
      appendDifference(nomisTransaction.description, dpsTransaction.description, differences, "description")
      appendDifference(nomisTransaction.transactionType, dpsTransaction.transactionType, differences, "type")
      appendDifference(nomisTransaction.reference, dpsTransaction.reference, differences, "reference")
      appendDifference(nomisTransaction.entryDateTime, dpsTransaction.entryDateTime, differences, "entryDate")

      return differences.takeIf { it.isNotEmpty() }?.let {
        MismatchPrisonTransaction(
          nomisTransactionId = nomisTransactionId,
          dpsTransactionId = mappingDto.dpsTransactionId,
          differences = it,
        ).also { mismatch ->
          log.info("Prison Transaction mismatch found {}", mismatch)
          telemetryClient.trackEvent(
            "prison-transaction-reports-reconciliation-mismatch",
            telemetry + mapOf(
              "differences" to differences.keys.joinToString(),
            ),
          )
        }
      }
    } ?: run {
      log.info("No mapping found for nomis transaction $nomisTransactionId")
      telemetryClient.trackEvent(
        "prison-transaction-reports-reconciliation-mismatch-missing-mapping",
        mapOf("nomisTransactionId" to nomisTransactionId.toString()),
      )
    }
    null
  }.onFailure {
    telemetryClient.trackEvent(
      "prison-transaction-reports-reconciliation-mismatch-error",
      mapOf(
        "nomisTransactionId" to nomis.first().transactionId.toString(),
      ),
    )
  }.getOrNull()
}

private fun findMissingTransactionEntries(nomisAccountSummaries: List<TransactionEntry>, dpsAccountSummaries: List<TransactionEntry>): Pair<List<TransactionEntry>, List<TransactionEntry>> {
  val missingFromDps = nomisAccountSummaries - dpsAccountSummaries
  val missingFromNomis = dpsAccountSummaries - nomisAccountSummaries

  return missingFromNomis to missingFromDps
}

data class TransactionSummary(
  val prisonId: String,
  val nomisTransactionId: Long,
  val description: String?,
  val transactionType: String,
  val reference: String?,
  val entryDateTime: LocalDateTime,
  val entries: List<TransactionEntry>,
)
data class TransactionEntry(
  val accountCode: Int,
  val postingType: String,
  val amount: BigDecimal,
  val entrySequence: Int,
)

fun List<GeneralLedgerTransactionDto>.toTransactionSummary(): TransactionSummary = first().run {
  TransactionSummary(
    nomisTransactionId = transactionId,
    prisonId = caseloadId,
    description = description,
    transactionType = type,
    reference = reference,
    entryDateTime = transactionTimestamp,
    entries = this@toTransactionSummary.map { it.toTransactionEntry() },
  )
}
fun GeneralLedgerTransactionDto.toTransactionEntry() = TransactionEntry(
  accountCode = accountCode,
  postingType = postingType.value,
  amount = amount.setScale(2, RoundingMode.HALF_UP),
  entrySequence = generalLedgerEntrySequence,
)

fun SyncGeneralLedgerTransactionResponse.toTransactionSummary() = TransactionSummary(
  nomisTransactionId = legacyTransactionId!!,
  prisonId = caseloadId,
  description = description,
  transactionType = transactionType,
  reference = reference,
  entryDateTime = transactionTimestamp,
  entries = generalLedgerEntries.map { it.toTransactionEntry() },
)
fun GeneralLedgerEntry.toTransactionEntry() = TransactionEntry(
  accountCode = code,
  postingType = postingType.value,
  amount = amount.setScale(2, RoundingMode.HALF_UP),
  entrySequence = this.entrySequence,
)

data class MismatchPrisonTransaction(
  val nomisTransactionId: Long,
  val dpsTransactionId: String,
  val differences: Map<String, String>,
)

private fun appendDifference(
  nomisField: Any?,
  dpsField: Any?,
  differences: MutableMap<String, String>,
  fieldName: String,
) {
  if (nomisField != dpsField) differences[fieldName] = "nomis=$nomisField, dps=$dpsField"
}
