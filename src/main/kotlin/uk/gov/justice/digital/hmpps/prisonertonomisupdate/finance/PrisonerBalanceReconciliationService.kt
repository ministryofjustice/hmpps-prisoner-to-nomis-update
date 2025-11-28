package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.math.BigDecimal

@Service
class PrisonerBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val financeNomisApiService: FinanceNomisApiService,
  private val nomisApiService: NomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  @Value("\${reports.prisoner.balance.reconciliation.page-size:10}") private val pageSize: Int = 10,
  @Value("\${reports.prisoner.balance.reconciliation.filter-prison:#{null}}") private val filterPrison: String?,
) {
  private companion object {
    private const val TELEMETRY_PRISONER_PREFIX = "prisoner-balance-reconciliation"
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun manualCheckPrisonerBalance(rootOffenderId: Long): MismatchPrisonerBalance? = checkPrisonerBalance(rootOffenderId)

  suspend fun manualCheckPrisonerBalance(offenderNo: String): MismatchPrisonerBalance? {
    val prisonerDetails = nomisApiService.getPrisonerDetails(offenderNo)
      ?: throw NotFoundException("offenderNo $offenderNo not found")
    return checkPrisonerBalance(prisonerDetails.rootOffenderId!!)
    // rootOffenderId is nullable but there are no nulls in the table in prod
  }

  suspend fun manualCheckSinglePrisonBalances(filterPrisonId: String): ReconciliationResult<MismatchPrisonerBalance> = generatePrisonerBalanceReconciliationReport(listOf(filterPrisonId))

  suspend fun generatePrisonerBalanceReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-requested",
      if (filterPrison != null) mapOf("filter-prison" to filterPrison) else emptyMap(),
    )

    runCatching { generatePrisonerBalanceReconciliationReport(filterPrison?.split(",")) }
      .onSuccess {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-report",
          mapOf(
            "balance-count" to it.itemsChecked.toString(),
            "page-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
            "filter-prison" to (filterPrison ?: ""),
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

  private suspend fun generatePrisonerBalanceReconciliationReport(filterPrisonId: List<String>?): ReconciliationResult<MismatchPrisonerBalance> = generateReconciliationReport(
    threadCount = pageSize,
    checkMatch = ::checkPrisonerBalance,
    nextPage = { id -> this.getPrisonerIdsForPage(id, filterPrisonId) },
  )

  internal suspend fun checkPrisonerBalance(rootOffenderId: Long): MismatchPrisonerBalance? = runCatching {
    val nomisResponse = financeNomisApiService.getPrisonerAccountDetails(rootOffenderId)
    val dpsResponse = dpsApiService.getPrisonerAccounts(nomisResponse.prisonNumber)
    val nomisFields = BalanceFields(
      prisonNumber = nomisResponse.prisonNumber,
      accounts = nomisResponse.accounts.map {
        AccountFields(
          prisonId = it.prisonId,
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
          prisonId = it.prisonId,
          balance = it.totalBalance,
          holdBalance = it.holdBalance,
          accountCode = it.accountCode,
        )
      },
    )

    val differenceList = compareObjects(dpsFields, nomisFields, "prisoner-balances")

    // log.info("compared\n$dpsFields with\n$nomisFields with result\n$differenceList")

    if (differenceList.isNotEmpty()) {
      // log.info("Differences: ${objectMapper.writeValueAsString(differenceList)}")
      telemetryClient.trackEvent(
        "prisoner-balance-reports-reconciliation-mismatch",
        mapOf(
          "prisoner" to nomisResponse.prisonNumber,
        ) + differenceList.associate { it.property to it.toString() },
      )
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
          compareBy<AccountFields> { it.prisonId }
            .thenBy { it.accountCode }
            .thenBy { it.balance }
            .thenBy { it.holdBalance },
        )
        val sortedNomisAppearances = nomisObj.accounts.sortedWith(
          compareBy<AccountFields> { it.prisonId }
            .thenBy { it.accountCode }
            .thenBy { it.balance }
            .thenBy { it.holdBalance },
        )

        differences.addAll(compareLists(sortedDpsAppearances, sortedNomisAppearances, "$parentProperty.accounts"))
      }

      is AccountFields -> {
        nomisObj as AccountFields
        if (dpsObj.prisonId.compareTo(nomisObj.prisonId) != 0) {
          differences.add(Difference("$parentProperty.prisonId", dpsObj.prisonId, nomisObj.prisonId))
        }
        if (dpsObj.balance.compareTo(nomisObj.balance) != 0) {
          differences.add(Difference("$parentProperty.balance", dpsObj.balance, nomisObj.balance))
        }
        val nomisRealBalance = nomisObj.holdBalance ?: BigDecimal.ZERO
        if (
          // DPS holdBalance is non-null
          dpsObj.holdBalance?.compareTo(nomisRealBalance) != 0
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

  internal suspend fun getPrisonerIdsForPage(lastOffenderId: Long, filterPrisonIds: List<String>? = null): ReconciliationPageResult<Long> = runCatching {
    financeNomisApiService.getPrisonerBalanceIdentifiersFromId(
      rootOffenderId = lastOffenderId,
      pageSize = pageSize,
      prisonIds = filterPrisonIds,
    )
  }.fold(
    onSuccess = { rootOffenderIdsWithLast ->
      ReconciliationSuccessPageResult(
        ids = rootOffenderIdsWithLast.rootOffenderIds,
        last = rootOffenderIdsWithLast.lastOffenderId,
      )
        .also { it -> log.info("Page requested from offenderId: $lastOffenderId, with ${it.ids.size} prisoners") }
    },
    onFailure = {
      telemetryClient.trackEvent(
        "prisoner-balance-reports-reconciliation-mismatch-page-error",
        mapOf(
          "lastOffenderId" to lastOffenderId.toString(),
          "error" to (it.message ?: ""),
        ),
      )
      log.error("Unable to match entire page of prisoners from offenderId: $lastOffenderId", it)
      ReconciliationErrorPageResult(it)
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
  val prisonId: String,
  val balance: BigDecimal,
  val holdBalance: BigDecimal?,
  val accountCode: Int,
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
