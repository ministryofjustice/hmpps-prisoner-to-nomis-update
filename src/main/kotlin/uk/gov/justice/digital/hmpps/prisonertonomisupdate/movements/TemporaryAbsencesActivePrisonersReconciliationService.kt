package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTemporaryAbsences
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class TemporaryAbsencesActivePrisonersReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: ExternalMovementsNomisApiService,
  private val dpsApiService: ExternalMovementsDpsApiService,
  private val nomisPrisonerApiService: NomisApiService,
  @param:Value($$"${reports.temporary-absences.active-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.temporary-absences.active-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_ACTIVE_TAPS = "temporary-absences-active-reconciliation"
  }

  suspend fun generateTapActivePrisonersReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_TAPS-requested",
      mapOf(),
    )

    runCatching { generateTapActivePrisonersReconciliationReport() }
      .onSuccess {
        log.info("Temporary absences active prisoners reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_ACTIVE_TAPS-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_ACTIVE_TAPS-report", mapOf("success" to "false"))
        log.error("Temporary absences all prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerIds>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateTapActivePrisonersReconciliationReport(): ReconciliationResult<PrisonerIds> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonerTapsMatch,
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
      "$TELEMETRY_ACTIVE_TAPS-mismatch-page-error",
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

  suspend fun checkPrisonerTapsMatch(prisonerIds: PrisonerIds): PrisonerIds? = runCatching {
    checkPrisonerTapsMatch(prisonerIds.offenderNo, prisonerIds.bookingId).takeIf { it.isNotEmpty() }?.let { prisonerIds }
  }.onFailure {
    log.error("Unable to match temporary absences for prisoner ${prisonerIds.offenderNo} booking ${prisonerIds.bookingId}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_TAPS-mismatch-error",
      mapOf(
        "offenderNo" to prisonerIds.offenderNo,
        "bookingId" to prisonerIds.bookingId,
      ),
    )
  }.getOrNull()

  suspend fun checkPrisonerTapsMatch(offenderNo: String, bookingId: Long): List<MismatchPrisonerTaps> {
    val (nomisTaps, dpsTaps) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getBookingTemporaryAbsences(bookingId) } to
        async { dpsApiService.getTapReconciliationDetail(offenderNo) }
    }.awaitBoth()
    return checkTapsMatch(
      offenderNo = offenderNo,
      bookingId = bookingId,
      dpsTaps = dpsTaps,
      nomisTaps = nomisTaps,
    )
  }

  private fun checkTapsMatch(offenderNo: String, bookingId: Long, dpsTaps: PersonTapDetail, nomisTaps: BookingTemporaryAbsences?): List<MismatchPrisonerTaps> {
    // TODO work out how to compare NOMIS / DPS - e.g. filter all active NOMIS TAPs , call mapping service to get IDs and get DPS entities, then compare? Or get all DPS active TAPs from a new endpoint?
    val mismatches = mutableListOf<MismatchPrisonerTaps>()
    mismatches.forEach {
      telemetryClient.trackEvent(
        "$TELEMETRY_ACTIVE_TAPS-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "bookingId" to bookingId,
          "type" to it.type.name,
          "nomisCount" to it.nomisCount.toString(),
          "dpsCount" to it.dpsCount.toString(),
        ),
      )
    }

    return mismatches.toList()
  }
}
