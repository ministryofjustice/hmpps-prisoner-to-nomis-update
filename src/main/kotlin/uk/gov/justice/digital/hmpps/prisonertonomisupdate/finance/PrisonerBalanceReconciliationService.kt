package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEventOrSuppress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateRangesReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

private const val RETRY_LIMIT = 200

@Service
class PrisonerBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val financeNomisApiService: FinanceNomisApiService,
  private val nomisApiService: NomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  @Value($$"${reports.prisoner.balance.reconciliation.page-size:1000}") private val pageSize: Int = 1000,
  @Value($$"${reports.prisoner.balance.reconciliation.thread-count:10}") private val threadCount: Int = 10,
  @Value($$"${reports.prisoner.balance.reconciliation.holds:false}") private val reconcileHolds: Boolean = false,
  @Value($$"${reports.prisoner.balance.reconciliation.balances:true}") private val reconcileBalances: Boolean = true,
) {
  private companion object {
    private const val TELEMETRY_PRISONER_PREFIX = "prisoner-balance-reports-reconciliation"
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val retryRootOffenders = ConcurrentHashMap.newKeySet<Long>()
  }

  suspend fun manualCheckPrisonerBalance(rootOffenderId: Long, suppressEvents: Boolean): MismatchPrisonerBalance? = checkPrisonerBalance(
    rootOffenderId,
    isRetry = true,
    suppressEvents = suppressEvents,
  )

  suspend fun manualCheckPrisonerBalance(offenderNo: String, suppressEvents: Boolean): MismatchPrisonerBalance? {
    val prisonerDetails = nomisApiService.getPrisonerDetails(offenderNo)
      ?: throw NotFoundException("offenderNo $offenderNo not found")
    return checkPrisonerBalance(prisonerDetails.rootOffenderId!!, isRetry = true, suppressEvents = suppressEvents)
    // rootOffenderId is nullable but there are no nulls in the table in prod
  }

  suspend fun generateReconciliationReportBatch(activeOnly: Boolean) {
    telemetryClient.trackEvent(
      "$TELEMETRY_PRISONER_PREFIX-requested",
      mapOf(
        "activeOnly" to activeOnly,
        "balances" to reconcileBalances,
        "holds" to reconcileHolds,
      ),
    )

    runCatching { generateReconciliationReport(activeOnly) }
      .onSuccess {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-report",
          mapOf(
            "activeOnly" to activeOnly,
            "balance-count" to it.itemsChecked,
            "page-count" to it.pagesChecked,
            "mismatch-count" to it.mismatches.size,
            "success" to "true",
          ),
        )
      }
      .onFailure {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-report",
          mapOf(
            "success" to "false",
            "error" to (it.message ?: it.javaClass.name),
          ),
        )
        log.error("Prisoner balance reconciliation report failed", it)
      }
  }

  private suspend fun generateReconciliationReport(activeOnly: Boolean): ReconciliationResult<MismatchPrisonerBalance> {
    retryRootOffenders.clear()
    return generateRangesReconciliationReport(
      threadCount = threadCount,
      checkMatch = ::checkPrisonerBalance,
      idRanges = { nomisApiService.getAllPrisonersIdRanges(pageSize.toLong(), activeOnly) },
      idsInRange = { range -> this.getOffenderIdsInRange(range.fromId, range.toId, activeOnly) },
    )
      .let { mainResults ->
        log.info("Retrying reconciliations for ${retryRootOffenders.size} prisoners")
        val retriedResults = retryRootOffenders.mapNotNull { checkPrisonerBalance(it, isRetry = true) }
        ReconciliationResult(
          itemsChecked = mainResults.itemsChecked,
          pagesChecked = mainResults.pagesChecked,
          mismatches = mainResults.mismatches + retriedResults,
        )
      }
  }

  internal suspend fun checkPrisonerBalance(rootOffenderId: Long, isRetry: Boolean = false, suppressEvents: Boolean = false): MismatchPrisonerBalance? = runCatching {
    val nomisAccounts = financeNomisApiService.getPrisonerAccountsToReconcile(rootOffenderId)
    val dpsAccounts = dpsApiService.getPrisonerAccounts(nomisAccounts.prisonNumber)
    val nomisFields = BalanceFields(
      prisonNumber = nomisAccounts.prisonNumber,
      accounts = nomisAccounts.accounts.filter { it.balance.compareTo(BigDecimal.ZERO) != 0 }
        .map {
          AccountFields(
            accountCode = it.accountCode.toInt(),
            balance = it.balance,
            holdBalance = it.holdBalance,
          )
        },
    )
    val dpsFields = BalanceFields(
      prisonNumber = nomisAccounts.prisonNumber,
      accounts = dpsAccounts.filter { it.value.totalBalance.compareTo(BigDecimal.ZERO) != 0 }
        .map {
          AccountFields(
            accountCode = it.key.toInt(),
            balance = it.value.totalBalance,
            holdBalance = it.value.holdBalance,
          )
        },
    )

    val differenceList = compareObjects(dpsFields, nomisFields, "prisoner-balances")

    // log.info("$rootOffenderId compared\n$dpsFields with\n$nomisFields with result\n$differenceList")

    if (differenceList.isNotEmpty()) {
      // log.info("Differences: ${objectMapper.writeValueAsString(differenceList)}")
      if (isRetry) {
        telemetryClient.trackEventOrSuppress(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          mapOf(
            "prisoner" to nomisAccounts.prisonNumber,
          ) + differenceList.associate { it.property to it },
          suppressEvent = suppressEvents,
        )
        return MismatchPrisonerBalance(
          nomis = nomisFields,
          dps = dpsFields,
          differences = differenceList,
        )
      } else {
        retryRootOffenders.add(rootOffenderId)
        return null
      }
    } else {
      return null
    }
  }.onFailure {
    log.error("Unable to match prisoner balances for offenderId={}", rootOffenderId, it)
    if (isRetry || retryRootOffenders.size > RETRY_LIMIT) {
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch-error",
        mapOf(
          "rootOffenderId" to rootOffenderId,
          "error" to (it.message ?: it.javaClass.name),
        ),
      )
    } else {
      retryRootOffenders.add(rootOffenderId)
    }
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
        val sortedDpsAccounts = dpsObj.accounts.sortedWith(
          compareBy<AccountFields> { it.accountCode }
            .thenBy { it.balance },
        )
        val sortedNomisAccounts = nomisObj.accounts.sortedWith(
          compareBy<AccountFields> { it.accountCode }
            .thenBy { it.balance },
        )

        differences.addAll(compareLists(sortedDpsAccounts, sortedNomisAccounts, "$parentProperty.accounts"))
      }

      is AccountFields -> {
        nomisObj as AccountFields
        if (dpsObj.accountCode != nomisObj.accountCode) {
          differences.add(Difference("$parentProperty.accountCode", dpsObj.accountCode, nomisObj.accountCode))
        }
        if (reconcileBalances) {
          if (dpsObj.balance.compareTo(nomisObj.balance) != 0) {
            differences.add(
              Difference(
                "$parentProperty.balance: account code ${nomisObj.accountCode}",
                dpsObj.balance,
                nomisObj.balance,
              ),
            )
          }
        }
        if (reconcileHolds) {
          if (dpsObj.holdBalance?.compareTo(nomisObj.holdBalance ?: BigDecimal.ZERO) != 0) {
            differences.add(Difference("$parentProperty.holdBalance: account code ${nomisObj.accountCode}", dpsObj.holdBalance, nomisObj.holdBalance))
          }
        }
      }
    }
    return differences
  }

  internal suspend fun getOffenderIdsInRange(
    fromRootOffenderId: Long,
    toRootOffenderId: Long,
    activeOnly: Boolean,
  ): ReconciliationPageResult<Long> = runCatching {
    nomisApiService.getAllPrisonersInRange(
      fromRootOffenderId = fromRootOffenderId,
      toRootOffenderId = toRootOffenderId,
      activeOnly = activeOnly,
    )
  }.fold(
    onSuccess = { ids ->
      ReconciliationSuccessPageResult(ids = ids.map { it.rootOffenderId }, last = 0)
        .also { log.info("Page requested from fromRootOffenderId: $fromRootOffenderId, toRootOffenderId: $toRootOffenderId, with ${it.ids.size} prisoners") }
    },
    onFailure = {
      telemetryClient.trackEvent(
        "$TELEMETRY_PRISONER_PREFIX-mismatch-page-error",
        mapOf(
          "fromRootOffenderId" to fromRootOffenderId,
          "toRootOffenderId" to toRootOffenderId,
          "error" to (it.message ?: it.javaClass.name),
        ),
      )
      log.error("Unable to match entire page of prisoners from fromRootOffenderId: $fromRootOffenderId, toRootOffenderId: $toRootOffenderId", it)
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
  val accountCode: Int,
  val balance: BigDecimal,
  val holdBalance: BigDecimal? = null,
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
