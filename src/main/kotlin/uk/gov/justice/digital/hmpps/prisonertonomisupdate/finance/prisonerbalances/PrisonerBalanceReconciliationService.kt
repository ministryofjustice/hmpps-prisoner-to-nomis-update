package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.prisonerbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEventOrSuppress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateRangesReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.associate
import kotlin.collections.isNotEmpty

private const val RETRY_LIMIT = 200

@Service
class PrisonerBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val financeNomisApiService: FinanceNomisApiService,
  private val nomisApiService: NomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  @Value($$"${reports.prisoner.balance.reconciliation.page-size:1000}") private val pageSize: Int = 1000,
  @Value($$"${reports.prisoner.balance.reconciliation.thread-count:10}") private val threadCount: Int = 10,
  @Value($$"${reports.prisoner.balance.reconciliation.fields:balances}") private val reconcileFields: String? = null, // holds,balances,advances,scheduled-payments
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
        "fields" to (reconcileFields ?: "all"),
      ),
    )

    runCatching { generateReconciliationReport(activeOnly) }
      .onSuccess {
        telemetryClient.trackEvent(
          "$TELEMETRY_PRISONER_PREFIX-report",
          mapOf(
            "activeOnly" to activeOnly,
            "fields" to (reconcileFields ?: "all"),
            "prisoners-count" to it.itemsChecked,
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

  internal suspend fun checkPrisonerBalance(
    rootOffenderId: Long,
    isRetry: Boolean = false,
    suppressEvents: Boolean = false,
  ): MismatchPrisonerBalance? = runCatching {
    val nomisAccounts = financeNomisApiService.getPrisonerAccountsToReconcile(rootOffenderId)
    val dpsAccounts = dpsApiService.getPrisonerAccounts(nomisAccounts.prisonNumber)

    val nomisFields = BalanceFields(
      prisonNumber = nomisAccounts.prisonNumber,
      accounts = nomisAccounts.accounts
        .map {
          AccountFields(
            accountCode = it.accountCode.toInt(),
            balance = it.balance,
            holdBalance = it.holdBalance,
          )
        },
      advanceSummary = AdvanceSummary(
        advanceCount = nomisAccounts.advances.size,
        advanceBalance = BigDecimal(
          nomisAccounts.advances.sumOf { it.outstandingBalance },
        ).scaleByPowerOfTen(-2),
      ),
      scheduledPayments = ScheduledPaymentSummary(count = nomisAccounts.scheduledPayments.count.toInt(), weeklyPayment = BigDecimal(nomisAccounts.scheduledPayments.totalWeeklyPayment).scaleByPowerOfTen(-2)),
    )
    val dpsFields = dpsAccounts?.let {
      BalanceFields(
        prisonNumber = nomisAccounts.prisonNumber,
        accounts = dpsAccounts.subAccountBalances
          .map {
            AccountFields(
              accountCode = it.key.toInt(),
              balance = it.value.totalBalance,
              holdBalance = it.value.holdBalance,
            )
          },
        advanceSummary = AdvanceSummary(
          advanceCount = dpsAccounts.advances.advanceCount,
          advanceBalance = dpsAccounts.advances.advanceBalance,
        ),
        scheduledPayments = ScheduledPaymentSummary(count = dpsAccounts.subscriptions.subscriptionCount, weeklyPayment = dpsAccounts.subscriptions.weeklyPaymentAmount),
      )
    }
      ?: BalanceFields(
        prisonNumber = nomisAccounts.prisonNumber,
        accounts = emptyList(),
        advanceSummary = AdvanceSummary(advanceCount = 0, advanceBalance = BigDecimal.ZERO),
        scheduledPayments = ScheduledPaymentSummary(count = 0, weeklyPayment = BigDecimal.ZERO),
      )

    val differenceList = compareBalanceFields(nomisFields, dpsFields, "prisoner-balances")

    // log.info("$rootOffenderId compared\n$dpsFields with\n$nomisFields with result\n$differenceList")

    if (differenceList.isNotEmpty()) {
      log.info("Prisoner balance differences: $differenceList")
      if (isRetry) {
        telemetryClient.trackEventOrSuppress(
          "$TELEMETRY_PRISONER_PREFIX-mismatch",
          mapOf(
            "prisoner" to nomisAccounts.prisonNumber,
          ) + differenceList.associate { it.property to "nomis:${it.nomis}; dps:${it.dps}" },
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

  private fun compareBalanceFields(nomis: BalanceFields, dps: BalanceFields, parentProperty: String): List<Difference> {
    val differences = mutableListOf<Difference>()

    if (nomis.prisonNumber != dps.prisonNumber) {
      differences.add(Difference("$parentProperty.prisonNumber", nomis.prisonNumber, dps.prisonNumber))
    }

    differences.addAll(compareAccounts(nomis.accounts, dps.accounts, "$parentProperty.accounts"))
    differences.addAll(compareAdvanceSummary(nomis.advanceSummary, dps.advanceSummary, "$parentProperty.advanceSummary"))
    differences.addAll(compareScheduledPayments(nomis.scheduledPayments, dps.scheduledPayments, "$parentProperty.scheduledPayments"))

    return differences
  }

  private fun compareAdvanceSummary(nomis: AdvanceSummary?, dps: AdvanceSummary?, parentProperty: String): List<Difference> {
    if (!reconcileAdvances()) return emptyList()
    if (nomis == null && dps == null) return emptyList()
    if (nomis == null || dps == null) return listOf(Difference(parentProperty, nomis, dps))

    val differences = mutableListOf<Difference>()

    differences.addIfMismatch("$parentProperty.advanceBalance", nomis.advanceBalance, dps.advanceBalance)
    differences.addIfMismatch("$parentProperty.advanceCount", nomis.advanceCount, dps.advanceCount)
    return differences
  }

  private fun compareScheduledPayments(nomis: ScheduledPaymentSummary, dps: ScheduledPaymentSummary, parentProperty: String): List<Difference> {
    if (!reconcileScheduledPayments()) return emptyList()

    val differences = mutableListOf<Difference>()

    differences.addIfMismatch("$parentProperty.weeklyPayment", nomis.weeklyPayment, dps.weeklyPayment)
    differences.addIfMismatch("$parentProperty.count", nomis.count, dps.count)
    return differences
  }

  private fun compareAccounts(nomisAccounts: List<AccountFields>, dpsAccounts: List<AccountFields>, parentProperty: String): List<Difference> {
    val differences = mutableListOf<Difference>()

    val nomisAccountsByCode = nomisAccounts.associateBy { it.accountCode }
    val dpsAccountsByCode = dpsAccounts.associateBy { it.accountCode }

    (dpsAccountsByCode.keys + nomisAccountsByCode.keys)
      .sorted()
      .forEach { accountCode ->
        val dpsAccount = dpsAccountsByCode[accountCode]
        val nomisAccount = nomisAccountsByCode[accountCode]

        val nomisBalance = nomisAccount?.balance ?: BigDecimal.ZERO
        val dpsBalance = dpsAccount?.balance ?: BigDecimal.ZERO
        val nomisHoldBalance = nomisAccount?.holdBalance ?: BigDecimal.ZERO
        val dpsHoldBalance = dpsAccount?.holdBalance ?: BigDecimal.ZERO

        if (reconcileBalances() && dpsBalance.compareTo(nomisBalance) != 0) {
          differences.addIfMismatch("$parentProperty[$accountCode].balance", nomisBalance, dpsBalance)
        }

        if (reconcileHolds() && dpsHoldBalance.compareTo(nomisHoldBalance) != 0) {
          differences.add(
            Difference(
              "$parentProperty[$accountCode].holdBalance",
              nomisAccount?.holdBalance,
              dpsAccount?.holdBalance,
            ),
          )
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

  private fun reconcileBalances(): Boolean = reconcileFields.isNullOrBlank() || reconcileFields.contains("balances")
  private fun reconcileHolds(): Boolean = reconcileFields.isNullOrBlank() || reconcileFields.contains("holds")
  private fun reconcileAdvances(): Boolean = reconcileFields.isNullOrBlank() || reconcileFields.contains("advances")
  private fun reconcileScheduledPayments(): Boolean = reconcileFields.isNullOrBlank() || reconcileFields.contains("scheduled-payments")
}

data class MismatchPrisonerBalance(
  val nomis: BalanceFields,
  val dps: BalanceFields?,
  val differences: List<Difference> = emptyList(),
)

data class BalanceFields(
  val prisonNumber: String,
  val accounts: List<AccountFields> = emptyList(),
  val advanceSummary: AdvanceSummary? = null,
  val scheduledPayments: ScheduledPaymentSummary = ScheduledPaymentSummary(count = 0, weeklyPayment = BigDecimal.ZERO),
)

data class AccountFields(
  val accountCode: Int,
  val balance: BigDecimal,
  val holdBalance: BigDecimal? = null,
)

data class AdvanceSummary(
  val advanceCount: Int,
  val advanceBalance: BigDecimal,
)

data class ScheduledPaymentSummary(
  val count: Int,
  val weeklyPayment: BigDecimal,
)

data class Difference(val property: String, val nomis: Any?, val dps: Any?, val id: String? = null)

private fun MutableList<Difference>.addIfMismatch(propertyName: String, nomisValue: BigDecimal, dpsValue: BigDecimal) {
  if (nomisValue.compareTo(dpsValue) != 0) {
    add(Difference(propertyName, nomisValue, dpsValue))
  }
}

private fun MutableList<Difference>.addIfMismatch(propertyName: String, nomisValue: Int, dpsValue: Int) {
  if (nomisValue != dpsValue) {
    add(Difference(propertyName, nomisValue, dpsValue))
  }
}
