package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto

@Service
class VisitBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsVisitBalanceApiService: VisitBalanceDpsApiService,
  private val nomisVisitBalanceApiService: VisitBalanceNomisApiService,
  private val nomisApiService: NomisApiService,
  @Value($$"${reports.visit-balance.reconciliation.page-size}")
  private val prisonerPageSize: Int = 20,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val TELEMETRY_VISIT_BALANCE_PREFIX = "visitbalance-reports-reconciliation"
  }
  suspend fun generateReconciliationReport() {
    telemetryClient.trackEvent(
      "$TELEMETRY_VISIT_BALANCE_PREFIX-requested",
      mapOf(),
    )
    runCatching {
      generateReconciliationReport(
        threadCount = prisonerPageSize,
        checkMatch = ::checkVisitBalanceMatch,
        nextPage = ::getNextBookingsForPage,
      )
    }
      .onSuccess {
        log.info("Visit balance reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_VISIT_BALANCE_PREFIX-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) +
            it.mismatches.take(5).asPrisonerMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_VISIT_BALANCE_PREFIX-report", mapOf("success" to "false", "error" to (it.message ?: "unknown")))
        log.error("Visit balance reconciliation report failed", it)
      }
  }

  private fun List<MismatchVisitBalance>.asPrisonerMap(): Map<String, String> = this.associate { it.prisonNumber to "dpsBalance=${it.dpsVisitBalance}, nomisBalance=${it.nomisVisitBalance}" }

  private suspend fun getNextBookingsForPage(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = runCatching {
    nomisApiService.getAllLatestBookings(
      lastBookingId = lastBookingId,
      activeOnly = true,
      pageSize = prisonerPageSize,
    )
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_VISIT_BALANCE_PREFIX-mismatch-page-error",
      mapOf(
        "booking" to lastBookingId.toString(),
      ),
    )
    log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
  }
    .map {
      ReconciliationSuccessPageResult(
        ids = it.prisonerIds,
        last = it.lastBookingId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from booking: $lastBookingId, with $prisonerPageSize bookings") }

  suspend fun checkVisitBalanceMatch(prisonerId: PrisonerIds): MismatchVisitBalance? = runCatching {
    // Note that the lack of a balance is treated identically to a balance of 0, since it could be created and then not
    // used in either system
    val nomisVisitBalance = nomisVisitBalanceApiService.getVisitBalance(prisonerId.offenderNo)?.toBalance() ?: PrisonerBalance()
    val dpsVisitBalance = dpsVisitBalanceApiService.getVisitBalance(prisonerId.offenderNo)?.toBalance() ?: PrisonerBalance()

    return if (nomisVisitBalance != dpsVisitBalance) {
      MismatchVisitBalance(prisonNumber = prisonerId.offenderNo, dpsVisitBalance = dpsVisitBalance, nomisVisitBalance = nomisVisitBalance).also { mismatch ->
        log.info("VisitBalance mismatch found {}", mismatch)
        val telemetry = telemetryOf(
          "prisonNumber" to mismatch.prisonNumber,
          "nomisVisitBalance" to mismatch.nomisVisitBalance.visitBalance,
          "dpsVisitBalance" to mismatch.dpsVisitBalance.visitBalance,
          "nomisPrivilegedVisitBalance" to mismatch.nomisVisitBalance.privilegedVisitBalance,
          "dpsPrivilegedVisitBalance" to mismatch.dpsVisitBalance.privilegedVisitBalance,
        )
        // booking will be 0 if reconciliation run for single prisoner, in which case ignore
        prisonerId.bookingId.takeIf { it != 0L }?.let { telemetry["bookingId"] = it }
        telemetryClient.trackEvent("$TELEMETRY_VISIT_BALANCE_PREFIX-mismatch", telemetry)
      }
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match visit balance for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_VISIT_BALANCE_PREFIX-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()

  suspend fun checkVisitBalanceMatch(offenderNo: String): MismatchVisitBalance? = checkVisitBalanceMatch(PrisonerIds(0, offenderNo))
}

fun VisitBalanceResponse.toBalance() = PrisonerBalance(visitBalance = remainingVisitOrders, privilegedVisitBalance = remainingPrivilegedVisitOrders)
fun PrisonerBalanceDto.toBalance() = PrisonerBalance(visitBalance = voBalance, privilegedVisitBalance = pvoBalance)

data class MismatchVisitBalance(
  val prisonNumber: String,
  val dpsVisitBalance: PrisonerBalance,
  val nomisVisitBalance: PrisonerBalance,
)

data class PrisonerBalance(
  val visitBalance: Int = 0,
  val privilegedVisitBalance: Int = 0,
)
