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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapCounts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class TemporaryAbsencesAllPrisonersReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: ExternalMovementsNomisApiService,
  private val dpsApiService: ExternalMovementsDpsApiService,
  private val nomisPrisonerApiService: NomisApiService,
  @param:Value($$"${reports.temporary-absences.all-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.temporary-absences.all-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_ALL_TAPS = "temporary-absences-all-reconciliation"
  }

  suspend fun generateTapAllPrisonersReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_TAPS-requested",
      mapOf(),
    )

    runCatching { generateTapAllPrisonersReconciliationReport() }
      .onSuccess {
        log.info("Temporary absences all prisoners reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_TAPS-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_ALL_TAPS-report", mapOf("success" to "false"))
        log.error("Temporary absences all prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerId>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateTapAllPrisonersReconciliationReport(): ReconciliationResult<PrisonerId> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonerTapsMatch,
    nextPage = ::getNextPrisonersForPage,
  )

  private suspend fun getNextPrisonersForPage(lastOffenderId: Long): ReconciliationPageResult<PrisonerId> = runCatching {
    nomisPrisonerApiService.getAllPrisoners(fromId = lastOffenderId, pageSize = pageSize)
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_TAPS-mismatch-page-error",
      mapOf(
        "offenderId" to lastOffenderId.toString(),
      ),
    )
    log.error("Unable to match entire page of offenders from offender ID: $lastOffenderId", it)
  }
    .map {
      ReconciliationSuccessPageResult(
        ids = it.prisonerIds,
        last = it.lastOffenderId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from offender ID: $lastOffenderId, with $pageSize offenders") }

  suspend fun checkPrisonerTapsMatch(prisonerId: PrisonerId): PrisonerId? = runCatching {
    val (nomisTaps, dpsTaps) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getTemporaryAbsenceSummary(prisonerId.offenderNo) } to
        async { dpsApiService.getTapReconciliation(prisonerId.offenderNo) }
    }.awaitBoth()
    checkTapsMatch(
      offenderNo = prisonerId.offenderNo,
      dpsTaps = dpsTaps,
      nomisTaps = nomisTaps,
    ).takeIf { it.isNotEmpty() }?.let { prisonerId }
  }.onFailure {
    log.error("Unable to match temporary absences for prisoner with ${prisonerId.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_TAPS-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
      ),
    )
  }.getOrNull()

  private fun checkTapsMatch(offenderNo: String, dpsTaps: PersonTapCounts, nomisTaps: OffenderTemporaryAbsenceSummaryResponse): List<MismatchPrisonerTaps> {
    val mismatches = mutableListOf<MismatchPrisonerTaps>()
    if (dpsTaps.authorisations.count != nomisTaps.applications.count.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.AUTHORISATIONS, dpsTaps.authorisations.count, nomisTaps.applications.count.toInt())
    }
    if (dpsTaps.occurrences.count != nomisTaps.scheduledOutMovements.count.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.OCCURRENCES, dpsTaps.occurrences.count, nomisTaps.scheduledOutMovements.count.toInt())
    }
    if (dpsTaps.movements.scheduled.outCount != nomisTaps.movements.scheduled.outCount.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.SCHEDULED_OUT, dpsTaps.movements.scheduled.outCount, nomisTaps.movements.scheduled.outCount.toInt())
    }
    if (dpsTaps.movements.scheduled.inCount != nomisTaps.movements.scheduled.inCount.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.SCHEDULED_IN, dpsTaps.movements.scheduled.inCount, nomisTaps.movements.scheduled.inCount.toInt())
    }
    if (dpsTaps.movements.unscheduled.outCount != nomisTaps.movements.unscheduled.outCount.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.UNSCHEDULED_OUT, dpsTaps.movements.unscheduled.outCount, nomisTaps.movements.unscheduled.outCount.toInt())
    }
    if (dpsTaps.movements.unscheduled.inCount != nomisTaps.movements.unscheduled.inCount.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.UNSCHEDULED_IN, dpsTaps.movements.unscheduled.inCount, nomisTaps.movements.unscheduled.inCount.toInt())
    }
    mismatches.forEach {
      telemetryClient.trackEvent(
        "$TELEMETRY_ALL_TAPS-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "type" to it.type.name,
          "nomisCount" to it.nomisCount.toString(),
          "dpsCount" to it.dpsCount.toString(),
        ),
      )
    }

    return mismatches.toList()
  }
}

data class MismatchPrisonerTaps(
  val offenderNo: String,
  val type: TapMismatchTypes,
  val dpsCount: Int,
  val nomisCount: Int,
)

enum class TapMismatchTypes {
  AUTHORISATIONS,
  OCCURRENCES,
  SCHEDULED_OUT,
  SCHEDULED_IN,
  UNSCHEDULED_OUT,
  UNSCHEDULED_IN,
}
