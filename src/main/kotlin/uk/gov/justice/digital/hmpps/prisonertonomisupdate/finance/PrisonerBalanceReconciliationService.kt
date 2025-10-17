package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import java.math.BigDecimal

@Service
class PrisonerBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val financeNomisApiService: FinanceNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  private val objectMapper: ObjectMapper,
  @Value("\${reports.prisoner.balance.reconciliation.page-size:10}") private val pageSize: Int = 10,
) {
  private companion object {
    const val TELEMETRY_PRISONER_PREFIX = "prisoner-balance-reconciliation"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun manualCheckPrisonerBalance(rootOffenderId: Long): MismatchPrisonerBalance? = checkPrisonerBalance(rootOffenderId)

  suspend fun generatePrisonerBalanceReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-requested",
      mapOf(),
    )

    runCatching { generatePrisonerBalanceReconciliationReport() }
      .onSuccess {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-report",
          mapOf(
            "balance-count" to it.itemsChecked.toString(),
            "page-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ), // + it.mismatches, // .asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-report",
          mapOf(
            "success" to "false",
            "error" to (it.message ?: ""),
          ),
        )
        log.error("Prisoner contacts reconciliation report failed", it)
      }
  }

  private suspend fun generatePrisonerBalanceReconciliationReport(): ReconciliationResult<MismatchPrisonerBalance> = generateReconciliationReport<Long, MismatchPrisonerBalance>(
    threadCount = pageSize,
    checkMatch = ::checkPrisonerBalance,
    nextPage = ::getPrisonerIdsForPage,
  )

  internal suspend fun checkPrisonerBalance(rootOffenderId: Long): MismatchPrisonerBalance? = runCatching {
    val nomisResponse = financeNomisApiService.getPrisonerAccountDetails(rootOffenderId)
    val dpsResponse = dpsApiService.listPrisonerAccounts(nomisResponse.prisonNumber)
    val nomisFields = BalanceFields(
      prisonNumber = nomisResponse.prisonNumber,
      accounts = nomisResponse.accounts.map {
        AccountFields(
          balance = it.balance,
          holdBalance = it.holdBalance,
          accountCode = it.accountCode.toInt(),
        )
      },
    )
    val dpsFields = BalanceFields(
      prisonNumber = nomisResponse.prisonNumber,
      accounts = dpsResponse.items.map {
        AccountFields(
          balance = it.balance,
          holdBalance = it.holdBalance,
          accountCode = it.code,
        )
      },
    )

    val differenceList = compareObjects(dpsFields, nomisFields, "prisoner-balances")

    log.info("compared\n$dpsFields with\n$nomisFields with result\n$differenceList")

    if (differenceList.isNotEmpty()) {
      log.info("Differences: ${objectMapper.writeValueAsString(differenceList)}")
      return MismatchPrisonerBalance(
        nomis = nomisFields,
        dps = dpsFields,
        differences = differenceList,
      )
    } else {
      return null
    }
  }.onFailure {
    log.error("Unable to match prisoner balances for offenderId=$rootOffenderId", it)
  }.getOrNull()

  private fun <T> compareLists(dpsList: List<T>, nomisList: List<T>, parentProperty: String): List<Difference> {
    val differences = mutableListOf<Difference>()
    val maxSize = maxOf(dpsList.size, nomisList.size)
    if (dpsList.size != nomisList.size) {
      differences.add(Difference(parentProperty, dpsList.size, nomisList.size))
    } else {
      for (i in 0 until maxSize) {
        val dpsObj = dpsList.getOrNull(i)
        val nomisObj = nomisList.getOrNull(i)
        differences.addAll(compareObjects(dpsObj, nomisObj, "$parentProperty[$i]"))
      }
    }
    return differences
  }

  private fun compareObjects(dpsObj: Any?, nomisObj: Any?, parentProperty: String): List<Difference> {
    if (dpsObj == null && nomisObj == null) return emptyList()
    if (dpsObj == null || nomisObj == null || dpsObj::class != nomisObj::class) return listOf(Difference(parentProperty, dpsObj, nomisObj))

    val differences = mutableListOf<Difference>()

    when (dpsObj) {
      is BalanceFields -> {
        nomisObj as BalanceFields

        if (dpsObj.prisonNumber != nomisObj.prisonNumber) {
          differences.add(Difference("$parentProperty.prisonNumber", dpsObj.prisonNumber, nomisObj.prisonNumber))
        }
        val sortedDpsAppearances = dpsObj.accounts.sortedWith(
          compareBy<AccountFields> { it.accountCode }
            .thenBy { it.balance }
            .thenBy { it.holdBalance },
        )
        val sortedNomisAppearances = nomisObj.accounts.sortedWith(
          compareBy<AccountFields> { it.accountCode }
            .thenBy { it.balance }
            .thenBy { it.holdBalance },
        )

        differences.addAll(compareLists(sortedDpsAppearances, sortedNomisAppearances, "$parentProperty.accounts"))
      }

      is AccountFields -> {
        nomisObj as AccountFields
        if (dpsObj.balance.compareTo(nomisObj.balance) != 0) {
          differences.add(Difference("$parentProperty.balance", dpsObj.balance, nomisObj.balance))
        }
        if (
          // DPS holdBalance is non-null
          nomisObj.holdBalance == null ||
          dpsObj.holdBalance?.compareTo(nomisObj.holdBalance) != 0
        ) {
          differences.add(Difference("$parentProperty.holdBalance", dpsObj.holdBalance, nomisObj.holdBalance))
        }
        if (dpsObj.accountCode != nomisObj.accountCode) {
          differences.add(Difference("$parentProperty.accountCode", dpsObj.accountCode, nomisObj.accountCode))
        }
      }
    }
    return differences
  }

  internal suspend fun getPrisonerIdsForPage(lastOffenderId: Long): ReconciliationPageResult<Long> = runCatching {
    financeNomisApiService.getPrisonerBalanceIdentifiersFromId(
      rootOffenderId = lastOffenderId,
      pageSize = pageSize,
    )
  }.fold(
    onSuccess = { rootOffenderIdsWithLast ->
      ReconciliationSuccessPageResult<Long>(
        ids = rootOffenderIdsWithLast.rootOffenderIds,
        last = rootOffenderIdsWithLast.lastOffenderId,
      )
        .also { it -> log.info("Page requested from offenderId: $lastOffenderId, with ${it.ids.size} prisoners") }
    },
    onFailure = {
      telemetryClient.trackEvent(
        "prisoner-balance-mismatch-page-error",
        mapOf(
          "lastOffenderId" to lastOffenderId.toString(),
          "error" to (it.message ?: ""),
        ),
      )
      log.error("Unable to match entire page of prisoners from offenderId: $lastOffenderId", it)
      ReconciliationErrorPageResult<Long>(it)
    },
  )
}

data class MismatchPrisonerBalance(
  val nomis: BalanceFields,
  val dps: BalanceFields,
  val differences: List<Difference> = emptyList(),
)

data class BalanceFields(
  val prisonNumber: String,
  val accounts: List<AccountFields> = emptyList(),
)

data class AccountFields(
  val balance: BigDecimal,
  val holdBalance: BigDecimal?,
  val accountCode: Int,
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
