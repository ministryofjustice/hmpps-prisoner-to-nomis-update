package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import java.math.BigDecimal

class PrisonerBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val financeNomisApiService: FinanceNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  private val objectMapper: ObjectMapper,
  @Value("\${reports.prisoner.balance.reconciliation.page-size:10}") private val pageSize: Int = 10,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generatePrisonerBalanceReconciliationReport()
    : ReconciliationResult<MismatchPrisonerBalance> = generateReconciliationReport<Long, MismatchPrisonerBalance>(
    threadCount = pageSize,
    checkMatch = ::checkPrisonerBalance,
    nextPage = ::getPrisonerIdsForPage,
  )

  suspend fun checkPrisonerBalance(offenderId: Long): MismatchPrisonerBalance? = runCatching {
    val nomisResponse = financeNomisApiService.getPrisonerAccountDetails(offenderId)
    val dpsResponse = dpsApiService.listPrisonerAccounts(nomisResponse.prisonNumber)
//    val dpsResponses = nomisResponse.accounts.map {
//      dpsApiService.getPrisonerSubAccountDetails(nomisResponse.prisonNumber, it.accountCode.toInt())
//    }
    val nomisFields = BalanceFields(
      prisonNumber = nomisResponse.prisonNumber,
      accounts = nomisResponse.accounts.map {
        AccountFields(
          balance = it.balance,
          holdBalance = it.holdBalance,
        )
      }
    )
    val dpsFields = BalanceFields(
      prisonNumber = nomisResponse.prisonNumber,
      accounts = dpsResponse.items.map {
        AccountFields(
          balance = it.balance,
          holdBalance = it.holdBalance,
        )
      },
    )

    val differenceList = compareObjects(dpsFields, nomisFields, "prisoner-balances")

    if (differenceList.isNotEmpty()) {
    // if (dpsFields != nomisFields) {
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
    log.error("Unable to match prisoner balance for offenderId=$offenderId", it)
  }.getOrNull()

  fun <T> compareLists(dpsList: List<T>, nomisList: List<T>, parentProperty: String): List<Difference> {
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

  fun compareObjects(dpsObj: Any?, nomisObj: Any?, parentProperty: String): List<Difference> {
     if (dpsObj == null && nomisObj == null) return emptyList()
     if (dpsObj == null || nomisObj == null) return listOf(Difference(parentProperty, dpsObj, nomisObj))
     if (dpsObj::class != nomisObj::class) return listOf(Difference(parentProperty, dpsObj, nomisObj))

    val differences = mutableListOf<Difference>()

    when (dpsObj) {
      is BalanceFields -> {
        nomisObj as BalanceFields

        if (dpsObj.prisonNumber != nomisObj.prisonNumber) {
          differences.add(Difference("$parentProperty.prisonNumber", dpsObj.prisonNumber, nomisObj.prisonNumber)) // , id = dpsObj.id))
        }
        val sortedDpsAppearances = dpsObj.accounts.sortedWith(
          compareBy<AccountFields> { it.balance }
            .thenBy { it.holdBalance },
        )
        val sortedNomisAppearances = nomisObj.accounts.sortedWith(
          compareBy<AccountFields> { it.balance }
            .thenBy { it.holdBalance },
        )

        differences.addAll(compareLists(sortedDpsAppearances, sortedNomisAppearances, "$parentProperty.accounts"))
      }
      is AccountFields -> {
        nomisObj as AccountFields
        if (dpsObj.balance != nomisObj.balance) {
          differences.add(Difference("$parentProperty.balance", dpsObj.balance, nomisObj.balance)) // , id = dpsObj.id))
        }
        if (dpsObj.holdBalance != nomisObj.holdBalance) {
          differences.add(Difference("$parentProperty.holdBalance", dpsObj.holdBalance, nomisObj.holdBalance)) // , id = dpsObj.id))
        }
      }
    }
    return differences


  }
//  suspend fun checkTransactions(
//    nomisLists: PrisonerTransactionLists,
//    dpsLists: List<DpsTransaction>,
//  ): List<MismatchTransactionResponse> =
//    nomisLists.offenderTransactions
//      .map {
//        val dpsId = dpsLists.find { }
//
//        MismatchTransactionResponse(
//          offenderNo = "xxx",
//          nomisTransactionId = it.transactionId,
//          dpsTransactionId = dpsId,
//          mismatch = checkTransaction(dpsTransactionId = dpsId, nomisTransactionId = it.transactionId),
//        )
//      } +
//      nomisLists.orphanGlTransactions
//        .map {
//          val dpsId: UUID = dpsLists.find { }
//
//          MismatchTransactionResponse(
//            offenderNo = "xxx",
//            nomisTransactionId = it.transactionId,
//            dpsTransactionId = dpsId,
//            mismatch = checkTransaction(dpsTransactionId = dpsId, nomisTransactionId = it.transactionId),
//          )
//        }


//  suspend fun manualCheckCaseOffenderNo(prisonerId: Long): List<MismatchTransactionResponse> = checkTransactions(
//    nomisLists = financeNomisApiService.getPrisonerAccountDetails(prisonerId),
//    dpsLists = dpsApiService.getPrisonerSubAccountDetails(prisonerId),
//  )

//  suspend fun checkPrisonerBalancesMatch(prisonerId: Long): MismatchPrisonerBalance? = runCatching {
//    manualCheckCaseOffenderNo(prisonerId)
//      .filter { it.mismatch != null }
//      .takeIf { it.isNotEmpty() }?.let {
//        MismatchPrisonerTransactionsResponse(
//          offenderNo = prisonerId,
//          mismatches = it,
//        )
//      }?.also {
//        it.mismatches.forEach { mismatch ->
//          telemetryClient.trackEvent(
//            "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-mismatch",
//            mapOf(
//              "offenderNo" to it.offenderNo,
//              "dpsTransactionId" to mismatch.dpsTransactionId.toString(),
//              "nomisTransactionId" to mismatch.nomisTransactionId.toString(),
//              "mismatchCount" to mismatch.mismatch!!.differences.size.toString(),
//            ),
//            null,
//          )
//        }
//      }
//  }.onFailure {
//    log.error("Unable to match prisoner: ${id.offenderNo}", it)
//    telemetryClient.trackEvent(
//      "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-error",
//      mapOf(
//        "offenderNo" to id.offenderNo,
//        "reason" to (it.message ?: "unknown"),
//      ),
//      null,
//    )
//  }.getOrNull()

  private suspend fun getPrisonerIdsForPage(lastBookingId: Long): ReconciliationPageResult<Long> = runCatching {
    financeNomisApiService.getPrisonerBalanceIdentifiersFromId(
      rootOffenderId = lastBookingId,
      pageSize = pageSize,
    )
  }.fold(
    onSuccess = { rootOffenderIdsWithLast ->
      ReconciliationSuccessPageResult<Long>(
        ids = rootOffenderIdsWithLast.rootOffenderIds.map { it },
        last = rootOffenderIdsWithLast.lastOffenderId,
      )
        .also { it -> log.info("Page requested from booking: $lastBookingId, with ${it.ids.size} prisoners") }
    },
    onFailure = {
      telemetryClient.trackEvent(
        "prisoner-balance-mismatch-page-error",
        mapOf(
          "booking" to lastBookingId.toString(),
        ),
      )
      log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
      ReconciliationErrorPageResult<Long>(it)
    },
  )
}

//data class MismatchPrisonerTransactionsResponse(
//  val offenderNo: String,
//  val mismatches: List<MismatchTransactionResponse>,
//)

//data class MismatchTransactionResponse(
//  val offenderNo: String = "TODO",
//  val dpsTransactionId: UUID,
//  val nomisTransactionId: Long,
//  val mismatch: MismatchPrisonerBalance?,
//)

data class MismatchPrisonerBalance(
  val nomis: BalanceFields,
  val dps: BalanceFields,
  val differences: List<Difference> = emptyList(),
)

data class BalanceFields(
  val prisonNumber: String,
  val accounts: List<AccountFields> = emptyList(),
  /*
  // nomis, list of:
    public final val prisonId: String,
    public final val lastTransactionId: Long,
    public final val transactionDate: LocalDateTime,
    public final val accountCode: Long,
    public final val balance: BigDecimal,
    public final val holdBalance: BigDecimal? = null

    // DPS:
      public final val code: Int,
    public final val name: String,
    public final val prisonNumber: String,
    public final val balance: BigDecimal,
    public final val holdBalance: BigDecimal
  */
)

data class AccountFields(
  val balance: BigDecimal,
  val holdBalance: BigDecimal?,
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
