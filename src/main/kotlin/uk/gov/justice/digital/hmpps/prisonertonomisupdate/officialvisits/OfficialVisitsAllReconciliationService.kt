package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@OptIn(ExperimentalCoroutinesApi::class)
@Service
class OfficialVisitsAllReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
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

    return ReconciliationResult(
      mismatches = emptyList(),
      itemsChecked = 0,
      pagesChecked = 0,
    )
  }

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
}

data class MismatchVisit(
  val nomisVisitId: Long,
)
