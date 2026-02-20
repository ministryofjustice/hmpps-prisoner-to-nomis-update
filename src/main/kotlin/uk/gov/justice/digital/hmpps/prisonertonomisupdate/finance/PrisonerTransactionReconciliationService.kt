package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.UUID

@Service
class PrisonerTransactionReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val transactionNomisApiService: TransactionNomisApiService,
  private val mappingApiService: TransactionMappingApiService,
  private val dpsApiService: FinanceDpsApiService,
) {
  private companion object {
    private const val TELEMETRY_PRISONER_PREFIX = "prisoner-transaction-reconciliation"
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport() {
    // TODO Full reconciliation
  }

  suspend fun checkTransactionMatch(nomisTransactionId: Long): MismatchPrisonerTransaction? = runCatching {
    val (nomisTransaction, dpsTransactionResult: DpsTransactionResult) = withContext(Dispatchers.Unconfined) {
      async {
        transactionNomisApiService.getPrisonerTransaction(nomisTransactionId).ifEmpty {
          throw NotFoundException("Prisoner transaction $nomisTransactionId not found")
        }
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

    return when (dpsTransactionResult) {
      is NoMapping -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          mapOf(
            "nomisTransactionId" to nomisTransactionId.toString(),
            "offenderNo" to nomisTransaction.first().offenderNo,
            "reason" to "transaction-mapping-missing",
          ),
        )
        MismatchPrisonerTransaction(nomisTransactionId = nomisTransactionId)
      }

      is NoDpsTransaction -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          mapOf(
            "nomisTransactionId" to nomisTransactionId.toString(),
            "dpsTransactionId" to dpsTransactionResult.transactionId,
            "offenderNo" to nomisTransaction.first().offenderNo,
            "reason" to "dps-transaction-missing",
          ),
        )
        MismatchPrisonerTransaction(nomisTransactionId = nomisTransactionId)
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
              "offenderNo" to nomisTransaction.first().offenderNo,
              "reason" to "transaction-different-details",
            ),
          )
          MismatchPrisonerTransaction(nomisTransactionId = nomisTransactionId)
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

// TODO Add more fields
data class MismatchPrisonerTransaction(
  val nomisTransactionId: Long,
  // val dpsTransactionId: String,
  // val differences: Map<String, String>,
)

// TODO determine all values to reconcile
data class PrisonerTransactionSummary(
  val prisonId: String,
  val nomisTransactionId: Long,
  val entryDateTime: LocalDateTime,
  val entries: List<PrisonerTransactionEntry>,
)

data class PrisonerTransactionEntry(
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
  subAccountType = subAccountType,
  postingType = postingType.value,
  amount = amount.setScale(2, RoundingMode.HALF_UP),
  prisonEntries = generalLedgerEntries.map { it.toTransactionEntry() },
)

sealed interface DpsTransactionResult
data class Transaction(val transaction: SyncOffenderTransactionResponse) : DpsTransactionResult
class NoMapping : DpsTransactionResult
data class NoDpsTransaction(val transactionId: String) : DpsTransactionResult
