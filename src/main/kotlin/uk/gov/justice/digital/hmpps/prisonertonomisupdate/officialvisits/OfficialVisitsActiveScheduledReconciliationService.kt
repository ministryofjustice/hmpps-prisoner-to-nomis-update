package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate

@Service
class OfficialVisitsActiveScheduledReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val nomisPrisonerApiService: NomisApiService,
  @param:Value($$"${reports.official-visits.active-prisoners-visits.reconciliation.page-size}") private val pageSize: Int = 30,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_ACTIVE_VISITS_PREFIX = "official-visits-active-reconciliation"
  }

  suspend fun generateActiveScheduledVisitsReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_VISITS_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateActiveScheduledVisitsReconciliationReport() }
      .onSuccess {
        log.info("Official visits active scheduled reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_ACTIVE_VISITS_PREFIX-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_ACTIVE_VISITS_PREFIX-report", mapOf("success" to "false"))
        log.error("Official visits active scheduled reconciliation report failed", it)
      }
  }

  private fun List<MismatchPrisonerVisit>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateActiveScheduledVisitsReconciliationReport(): ReconciliationResult<MismatchPrisonerVisit> = generateReconciliationReport(
    threadCount = pageSize,
    checkMatch = ::checkPrisonerContactsMatch,
    nextPage = ::getNextBookingsForPage,
  )

  private suspend fun getNextBookingsForPage(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = runCatching {
    nomisPrisonerApiService.getAllLatestBookings(lastBookingId = lastBookingId, activeOnly = true, pageSize = pageSize)
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_VISITS_PREFIX-mismatch-page-error",
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
    .also { log.info("Page requested from booking: $lastBookingId, with $pageSize bookings") }

  suspend fun checkPrisonerContactsMatch(prisonerId: PrisonerIds): MismatchPrisonerVisit? = runCatching {
    val (nomisVisits, dpsVisits) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getOfficialVisitsForPrisoner(prisonerId.offenderNo, fromDate = LocalDate.now(), toDate = LocalDate.now().plusMonths(1)) } to
        async { dpsApiService.getOfficialVisitsForPrisoner(prisonerId.offenderNo, fromDate = LocalDate.now(), toDate = LocalDate.now().plusMonths(1)) }
    }.awaitBoth()
    checkVisitsMatch(
      offenderNo = prisonerId.offenderNo,
      dpsVisits = dpsVisits.map { it.toVisit() },
      nomisVisits = nomisVisits.map { it.toVisit() },
    ).asSummaryResult()
  }.onFailure {
    log.error("Unable to match official visits for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_VISITS_PREFIX-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()

  private fun checkVisitsMatch(offenderNo: String, dpsVisits: List<OfficialVisitSummary>, nomisVisits: List<OfficialVisitSummary>): List<MismatchPrisonerVisit> {
    val mismatches = mutableListOf<MismatchPrisonerVisit>()
    if (dpsVisits.size != nomisVisits.size) {
      mismatches.add(MismatchPrisonerVisit(offenderNo = offenderNo))
      telemetryClient.trackEvent(
        "$TELEMETRY_ACTIVE_VISITS_PREFIX-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "dpsCount" to dpsVisits.size.toString(),
          "nomisCount" to nomisVisits.size.toString(),
          "reason" to "visit-record-missing",
        ),
      )
    }
    dpsVisits.filter { !nomisVisits.contains(it) }.forEach { dpsVisit ->
      mismatches.add(MismatchPrisonerVisit(offenderNo = offenderNo))
      telemetryClient.trackEvent(
        "$TELEMETRY_ACTIVE_VISITS_PREFIX-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "startDateTime" to dpsVisit.startDateTime.toString(),
          "reason" to "dps-visit-not-found",
        ),
      )
    }
    nomisVisits.filter { !dpsVisits.contains(it) }.forEach { nomisVisit ->
      mismatches.add(MismatchPrisonerVisit(offenderNo = offenderNo))
      telemetryClient.trackEvent(
        "$TELEMETRY_ACTIVE_VISITS_PREFIX-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "startDateTime" to nomisVisit.startDateTime.toString(),
          "reason" to "nomis-visit-not-found",
        ),
      )
    }

    return mismatches.toList()
  }

  private fun List<MismatchPrisonerVisit>.asSummaryResult(): MismatchPrisonerVisit? = takeIf { it.isNotEmpty() }?.let { MismatchPrisonerVisit(offenderNo = it.first().offenderNo) }
}

data class MismatchPrisonerVisit(
  val offenderNo: String,
)
