package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.logger
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class AgencyRegistersReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: AgencyRegistersDpsApiService,
  private val nomisApiService: AgencyNomisApiService,
) {
  internal companion object {
    val log: Logger = logger()
    const val TELEMETRY_PREFIX = "agency-reconciliation"
  }

  suspend fun generateAgencyReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateAgencyReconciliationReport() }
      .onSuccess {
        log.info("Agency report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-report",
          mapOf(
            "agency-count" to it.itemsChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_PREFIX-report", mapOf("success" to "false"))
        log.error("Agency report failed", it)
      }
  }

  suspend fun generateAgencyReconciliationReport(): ReconciliationResult<MismatchAgency> {
    checkTotalsMatch()

    return ReconciliationResult(
      mismatches = emptyList(),
      itemsChecked = 0,
      pagesChecked = 0,
    )
  }

  private suspend fun checkTotalsMatch() = runCatching {
    val (nomisTotal, dpsTotal) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getAgencyIds().agencyIds.size } to
        async { dpsApiService.getAgencyIds().agencyIds.size }
    }.awaitBoth()

    if (nomisTotal != dpsTotal) {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-mismatch-totals",
        mapOf(
          "nomisTotal" to nomisTotal.toString(),
          "dpsTotal" to dpsTotal.toString(),
        ),
      )
    }
  }.onFailure {
    log.error("Unable to get agency totals", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-mismatch-totals-error",
      mapOf(),
    )
  }
}

data class MismatchAgency(
  val agencyId: String,
  val reason: String,
)

private fun List<MismatchAgency>.asMap(): Pair<String, String> = this
  .sortedBy { it.agencyId }.take(10).let { mismatch -> "agencyIds" to mismatch.map { it.agencyId }.joinToString() }
