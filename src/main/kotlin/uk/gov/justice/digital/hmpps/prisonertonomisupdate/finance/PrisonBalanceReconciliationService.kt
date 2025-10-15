package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerBalanceDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonAccountBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.math.BigDecimal
import kotlin.collections.plus
import kotlin.collections.toSet

@Service
class PrisonBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: FinanceDpsApiService,
  private val nomisApiService: FinanceNomisApiService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport() {
    val prisonIds = nomisApiService.getPrisonBalanceIds()

    telemetryClient.trackEvent(
      "prison-balance-reports-reconciliation-requested",
      mapOf("prisons" to prisonIds.size.toString()),
    )
    log.info("Prison balance reconciliation report requested for ${prisonIds.size} prisons")

    runCatching { generateReconciliationReport(prisonIds) }
      .onSuccess {
        log.info("Prison balance reconciliation report completed with ${it.size} mismatches")
        telemetryClient.trackEvent(
          "prison-balance-reports-reconciliation-report",
          mapOf(
            "mismatch-count" to it.size.toString(),
            "success" to "true",
          ),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("prison-balance-reports-reconciliation-report", mapOf("success" to "false"))
        log.error("Prison balance reconciliation report failed", it)
      }
  }

  suspend fun generateReconciliationReport(prisonIds: List<String>): List<MismatchPrisonBalance> = withContext(Dispatchers.Unconfined) {
    prisonIds.map { async { checkPrisonBalanceMatch(it) } }
  }.awaitAll().filterNotNull()

  suspend fun checkPrisonBalanceMatch(prisonId: String): MismatchPrisonBalance? = runCatching {
    val nomisPrisonBalances =
      doApiCallWithRetries { nomisApiService.getPrisonBalance(prisonId) }.accountBalances.map { it.toPrisonSummary() }
    val dpsPrisonBalances =
      doApiCallWithRetries { dpsApiService.getPrisonAccounts(prisonId) }.items.map { it.toPrisonSummary() }

    val (accountCodesInDpsOnly, accountCodesInNomisOnly) =
      findMissingPrisonBalances(nomisPrisonBalances, dpsPrisonBalances)

    val telemetry = mutableMapOf(
      "prisonId" to prisonId,
      "nomisAccountCount" to (nomisPrisonBalances.size.toString()),
      "dpsAccountCount" to (dpsPrisonBalances.size.toString()),
      "missingFromNomis" to accountCodesInDpsOnly.toString(),
      "missingFromDps" to accountCodesInNomisOnly.toString(),
    )

    if (nomisPrisonBalances.size != dpsPrisonBalances.size) {
      return MismatchPrisonBalance(
        prisonId,
        nomisAccountCount = nomisPrisonBalances.size,
        dpsAccountCount = dpsPrisonBalances.size,
        verdict = "different-number-of-accounts",
      ).also { mismatch ->
        log.info("Prison account sizes do not match $mismatch")
        telemetryClient.trackEvent(
          "prison-balance-reports-reconciliation-mismatch",
          telemetry + mapOf("reason" to "different-number-of-accounts"),
        )
      }
    }

    if (accountCodesInDpsOnly.isNotEmpty() || accountCodesInNomisOnly.isNotEmpty()) {
      return MismatchPrisonBalance(
        prisonId,
        nomisAccountCount = nomisPrisonBalances.size,
        dpsAccountCount = dpsPrisonBalances.size,
        verdict = "different-account-codes",
      ).also { mismatch ->
        log.info("Prison account codes missing $mismatch")
        telemetryClient.trackEvent(
          "prison-balance-reports-reconciliation-mismatch",
          telemetry + mapOf("reason" to "different-account-codes"),
        )
      }
    }

    return nomisPrisonBalances.filter { !dpsPrisonBalances.contains(it) }.map {
      telemetryClient.trackEvent(
        "prison-balance-reports-reconciliation-mismatch",
        telemetry + mapOf(
          "reason" to "different-prison-account-balance",
          "nomisPrisonBalances" to nomisPrisonBalances,
          "dpsPrisonBalances" to dpsPrisonBalances,
        ),
      )
      MismatchPrisonBalance(
        prisonId,
        nomisAccountCount = nomisPrisonBalances.size,
        dpsAccountCount = dpsPrisonBalances.size,
        verdict = "different-prison-account-balance",
      )
    }.firstOrNull()
  }.onFailure {
    log.error("Unable to match prison balance for prison with id $prisonId", it)
    telemetryClient.trackEvent(
      "prison-balance-reports-reconciliation-mismatch-error",
      mapOf(
        "prisonId" to prisonId,
      ),
    )
  }.getOrNull()
}

private fun findMissingPrisonBalances(nomisAccountSummaries: List<AccountSummary>, dpsAccountSummaries: List<AccountSummary>): Pair<List<Int>, List<Int>> {
  val nomisAccountCodes = nomisAccountSummaries.map { it.accountCode }.toSet()
  val dpsAccountCodes = dpsAccountSummaries.map { it.accountCode }.toSet()

  val missingFromDps = nomisAccountCodes - dpsAccountCodes
  val missingFromNomis = dpsAccountCodes - nomisAccountCodes

  return missingFromNomis.toList() to missingFromDps.toList()
}

fun PrisonAccountBalanceDto.toPrisonSummary() = AccountSummary(accountCode.toInt(), balance)
fun GeneralLedgerBalanceDetails.toPrisonSummary() = AccountSummary(accountCode, balance)

data class AccountSummary(
  val accountCode: Int,
  val balance: BigDecimal,
)

data class MismatchPrisonBalance(
  val prisonId: String,
  val dpsAccountCount: Int,
  val nomisAccountCount: Int,
  val missingFromNomis: List<Int> = listOf(),
  val missingFromDps: List<Int> = listOf(),
  val verdict: String,
)
