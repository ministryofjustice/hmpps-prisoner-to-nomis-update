package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerReconciliationService.Companion.TELEMETRY_COURT_SCHEDULER
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@Service
class CourtSchedulerReconciliationServiceActivePrisoners(
  private val telemetryClient: TelemetryClient,
  private val nomisPrisonerApiService: NomisApiService,
  private val reonciliationService: CourtSchedulerReconciliationService,
  @param:Value($$"${reports.temporary-absences.active-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.temporary-absences.active-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateCourtSchedulerReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_SCHEDULER-requested",
      mapOf(
        "TYPE" to "ACTIVE",
      ),
    )

    runCatching { generateCourtSchedulerReconciliationReport() }
      .onSuccess {
        log.info("Court scheduler reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_COURT_SCHEDULER-report",
          mapOf(
            "TYPE" to "ACTIVE",
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_COURT_SCHEDULER-report", mapOf("success" to "false"))
        log.error("Temporary absences all prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerIds>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateCourtSchedulerReconciliationReport(): ReconciliationResult<PrisonerIds> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonersMatch,
    nextPage = ::getNextBookingsForPage,
  )

  private suspend fun getNextBookingsForPage(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = runCatching {
    nomisPrisonerApiService.getAllLatestBookings(
      lastBookingId = lastBookingId,
      activeOnly = true,
      pageSize = pageSize,
    )
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_SCHEDULER-mismatch-page-error",
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
    .also { log.info("Page requested from booking: $lastBookingId, with $pageSize offenders") }

  suspend fun checkPrisonersMatch(prisonerIds: PrisonerIds): PrisonerIds? = runCatching {
    reonciliationService.checkPrisonersMatch(prisonerIds.offenderNo).takeIf { it.isNotEmpty() }
      ?.let { prisonerIds }
  }.onFailure {
    log.error("Unable to match court schedule for prisoner ${prisonerIds.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_SCHEDULER-mismatch-error",
      mapOf(
        "offenderNo" to prisonerIds.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
    )
  }.getOrNull()
}
