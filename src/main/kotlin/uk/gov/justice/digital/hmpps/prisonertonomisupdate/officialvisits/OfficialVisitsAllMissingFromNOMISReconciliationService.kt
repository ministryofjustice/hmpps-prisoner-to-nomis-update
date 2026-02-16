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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class OfficialVisitsAllMissingFromNOMISReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val mappingService: OfficialVisitsMappingService,
  @param:Value($$"${reports.official-visits.all-missing-nomis-visits.reconciliation.page-size}") private val pageSize: Int = 1000,
  @param:Value($$"${reports.official-visits.all-missing-nomis-visits.reconciliation.thread-count}") private val threadCount: Int = 30,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_ALL_MISSING_VISITS_PREFIX = "official-visits-all-nomis-missing-reconciliation"
  }

  suspend fun generateAllVisitsReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_MISSING_VISITS_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateAllMissingVisitsReconciliationReport() }
      .onSuccess {
        log.info("Official visits all missing reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_MISSING_VISITS_PREFIX-report",
          mapOf(
            "visit-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_ALL_MISSING_VISITS_PREFIX-report", mapOf("success" to "false"))
        log.error("Official visits all reconciliation report failed", it)
      }
  }

  private fun List<MissingVisit>.asMap(): Pair<String, String> = this
    .sortedBy { it.dpsVisitId }.take(10).let { mismatch -> "dpsVisitIds" to mismatch.map { it.dpsVisitId }.joinToString() }

  suspend fun generateAllMissingVisitsReconciliationReport(): ReconciliationResult<MissingVisit> {
    checkTotalsMatch()

    return generateReconciliationReport(
      threadCount = threadCount,
      pageSize = pageSize,
      checkMatch = ::checkNomisVisitExists,
      nextPage = ::getNextNomisVisitIdsForPage,
    )
  }

  private suspend fun getNextNomisVisitIdsForPage(pageNumber: Long): ReconciliationPageResult<Long> = runCatching {
    dpsApiService.getOfficialVisitIds(pageNumber = pageNumber.toInt(), pageSize = pageSize)
  }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_ALL_MISSING_VISITS_PREFIX-mismatch-page-error",
        mapOf(
          "pageNumber" to pageNumber.toString(),
        ),
      )
      log.error("Unable to match entire page of visits from page: $pageNumber", it)
    }
    .map { page -> ReconciliationSuccessPageResult(ids = page.content!!.map { it.officialVisitId }, last = pageNumber + 1) }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from page: $pageNumber, with $pageSize visits") }

  suspend fun checkNomisVisitExists(dpsVisitId: Long): MissingVisit? = runCatching {
    return when (val nomisResult = dpsVisitToPossibleNomisVisit(dpsVisitId)) {
      is NoNomisMapping -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_MISSING_VISITS_PREFIX-mismatch",
          mapOf(
            "dpsVisitId" to dpsVisitId.toString(),
            "reason" to "official-visit-mapping-missing",
          ),
        )
        MissingVisit(dpsVisitId = dpsVisitId, reason = "official-visit-mapping-missing")
      }

      is NoNomisOfficialVisit -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_MISSING_VISITS_PREFIX-mismatch",
          mapOf(
            "nomisVisitId" to nomisResult.nomisVisitId,
            "dpsVisitId" to dpsVisitId.toString(),
            "reason" to "nomis-record-missing",
          ),
        )
        MissingVisit(dpsVisitId = dpsVisitId, reason = "nomis-record-missing")
      }

      is NomisOfficialVisitExists -> null
    }
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_MISSING_VISITS_PREFIX-mismatch-error",
      mapOf(
        "dpsVisitId" to "$dpsVisitId",
      ),
    )
  }.getOrNull()

  private suspend fun dpsVisitToPossibleNomisVisit(dpsVisitId: Long): NomisOfficialVisitResult = withContext(Dispatchers.Unconfined) {
    val mapping = mappingService.getByDpsIdsOrNull(dpsVisitId)
    if (mapping != null) {
      val nomisOfficialVisit = nomisApiService.getOfficialVisitOrNull(mapping.nomisId)
      if (nomisOfficialVisit == null) {
        NoNomisOfficialVisit(mapping.nomisId)
      } else {
        NomisOfficialVisitExists(mapping.nomisId)
      }
    } else {
      NoNomisMapping()
    }
  }

  private suspend fun checkTotalsMatch() = runCatching {
    val (nomisTotal, dpsTotal) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getOfficialVisitIds().page!!.totalElements } to
        async { dpsApiService.getOfficialVisitIds().page!!.totalElements!! }
    }.awaitBoth()

    if (nomisTotal != dpsTotal) {
      telemetryClient.trackEvent(
        "$TELEMETRY_ALL_MISSING_VISITS_PREFIX-mismatch-totals",
        mapOf(
          "nomisTotal" to nomisTotal.toString(),
          "dpsTotal" to dpsTotal.toString(),
        ),
      )
    }
  }.onFailure {
    log.error("Unable to get official visit totals", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_MISSING_VISITS_PREFIX-mismatch-totals-error",
      mapOf(),
    )
  }
}

data class MissingVisit(
  val dpsVisitId: Long,
  val reason: String,
)

sealed interface NomisOfficialVisitResult
data class NomisOfficialVisitExists(val nomisVisitId: Long) : NomisOfficialVisitResult
class NoNomisMapping : NomisOfficialVisitResult
data class NoNomisOfficialVisit(val nomisVisitId: Long) : NomisOfficialVisitResult
