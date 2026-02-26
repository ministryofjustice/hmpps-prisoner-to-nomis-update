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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class OfficialVisitsAllReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val mappingService: OfficialVisitsMappingService,
  @param:Value($$"${reports.official-visits.all-visits.reconciliation.page-size}") private val pageSize: Int = 30,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_ALL_VISITS_PREFIX = "official-visits-all-reconciliation"
  }

  suspend fun generateAllVisitsReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_VISITS_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateAllVisitsReconciliationReport() }
      .onSuccess {
        log.info("Official visits all reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_VISITS_PREFIX-report",
          mapOf(
            "visit-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_ALL_VISITS_PREFIX-report", mapOf("success" to "false"))
        log.error("Official visits all reconciliation report failed", it)
      }
  }

  private fun List<MismatchVisit>.asMap(): Pair<String, String> = this
    .sortedBy { it.nomisVisitId }.take(10).let { mismatch -> "nomisVisitIds" to mismatch.map { it.nomisVisitId }.joinToString() }

  suspend fun generateAllVisitsReconciliationReport(): ReconciliationResult<MismatchVisit> {
    checkTotalsMatch()

    return generateReconciliationReport(
      threadCount = pageSize,
      checkMatch = ::checkVisitsMatch,
      nextPage = ::getNextNomisVisitIdsForPage,
    )
  }

  private suspend fun getNextNomisVisitIdsForPage(lastNomisVisitId: Long): ReconciliationPageResult<Long> = runCatching {
    nomisApiService.getOfficialVisitIdsByLastId(lastVisitId = lastNomisVisitId, pageSize = pageSize.toLong())
  }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_ALL_VISITS_PREFIX-mismatch-page-error",
        mapOf(
          "nomisVisitId" to lastNomisVisitId.toString(),
        ),
      )
      log.error("Unable to match entire page of visits from nomisVisitId: $lastNomisVisitId", it)
    }
    .map { page -> ReconciliationSuccessPageResult(ids = page.ids.map { it.visitId }, last = page.ids.last().visitId) }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from visit: $lastNomisVisitId, with $pageSize visits") }

  suspend fun checkVisitsMatch(nomisVisitId: Long): MismatchVisit? = runCatching {
    val (nomisVisit, dpsResult: DpsOfficialVisitResult) = nomisVisitToPossibleDpsVisit(nomisVisitId)

    return when (dpsResult) {
      is NoMapping -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_VISITS_PREFIX-mismatch",
          mapOf(
            "nomisVisitId" to nomisVisitId.toString(),
            "offenderNo" to nomisVisit.offenderNo,
            "reason" to "official-visit-mapping-missing",
          ),
        )
        MismatchVisit(nomisVisitId = nomisVisitId, reason = "official-visit-mapping-missing")
      }

      is NoOfficialVisit -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_VISITS_PREFIX-mismatch",
          mapOf(
            "nomisVisitId" to nomisVisitId.toString(),
            "dpsVisitId" to dpsResult.dpsVisitId,
            "offenderNo" to nomisVisit.offenderNo,
            "reason" to "dps-record-missing",
          ),
        )
        MismatchVisit(nomisVisitId = nomisVisitId, reason = "dps-record-missing")
      }

      is OfficialVisit -> {
        checkVisitsMatch(
          nomisVisitId = nomisVisitId,
          dpsVisit = dpsResult.visit.toVisit(),
          nomisVisit = nomisVisit.toVisit(),
        )?.also {
          telemetryClient.trackEvent(
            "$TELEMETRY_ALL_VISITS_PREFIX-mismatch",
            mapOf(
              "nomisVisitId" to nomisVisitId.toString(),
              "dpsVisitId" to dpsResult.visit.officialVisitId,
              "offenderNo" to nomisVisit.offenderNo,
              "reason" to "different-visit-details",
            ),
          )
        }
      }
    }
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_VISITS_PREFIX-mismatch-error",
      mapOf(
        "nomisVisitId" to "$nomisVisitId",
      ),
    )
  }.getOrNull()

  private suspend fun nomisVisitToPossibleDpsVisit(nomisVisitId: Long): Pair<OfficialVisitResponse, DpsOfficialVisitResult> = withContext(Dispatchers.Unconfined) {
    async { nomisApiService.getOfficialVisit(nomisVisitId) } to
      async {
        val mapping = mappingService.getVisitByNomisIdOrNull(nomisVisitId)
        if (mapping != null) {
          val dpsOfficialVisit = dpsApiService.getOfficialVisitOrNull(mapping.dpsId.toLong())
          if (dpsOfficialVisit == null) {
            NoOfficialVisit(mapping.dpsId)
          } else {
            OfficialVisit(dpsOfficialVisit)
          }
        } else {
          NoMapping()
        }
      }
  }.awaitBoth()

  private suspend fun checkTotalsMatch() = runCatching {
    val (nomisTotal, dpsTotal) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getOfficialVisitIds().page!!.totalElements } to
        async { dpsApiService.getOfficialVisitIds().page!!.totalElements!! }
    }.awaitBoth()

    if (nomisTotal != dpsTotal) {
      telemetryClient.trackEvent(
        "$TELEMETRY_ALL_VISITS_PREFIX-mismatch-totals",
        mapOf(
          "nomisTotal" to nomisTotal.toString(),
          "dpsTotal" to dpsTotal.toString(),
        ),
      )
    }
  }.onFailure {
    log.error("Unable to get official visit totals", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_VISITS_PREFIX-mismatch-totals-error",
      mapOf(),
    )
  }

  private fun checkVisitsMatch(nomisVisitId: Long, dpsVisit: OfficialVisitSummary, nomisVisit: OfficialVisitSummary): MismatchVisit? = takeIf { dpsVisit != nomisVisit }?.let {
    MismatchVisit(
      nomisVisitId = nomisVisitId,
      reason = "different-visit-details",
      nomisVisit = nomisVisit,
      dpsVisit = dpsVisit,
    )
  }
}

data class MismatchVisit(
  val nomisVisitId: Long,
  val reason: String,
  val nomisVisit: OfficialVisitSummary? = null,
  val dpsVisit: OfficialVisitSummary? = null,
)

sealed interface DpsOfficialVisitResult
data class OfficialVisit(val visit: SyncOfficialVisit) : DpsOfficialVisitResult
class NoMapping : DpsOfficialVisitResult
data class NoOfficialVisit(val dpsVisitId: String) : DpsOfficialVisitResult
