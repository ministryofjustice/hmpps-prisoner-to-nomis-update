package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingReconciliationService.Companion.TELEMETRY_COURT_CASE_PRISONER_PREFIX

@RestController
class CourtSentencingReconciliationResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: CourtSentencingReconciliationService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/court-sentencing/court-cases/prisoner/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateCourtCasePrisonerReconciliationReport() {
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-requested",
      mapOf(),
    )

    reportScope.launch {
      runCatching { reconciliationService.generatePrisonerCourtCasesReconciliationReport() }
        .onSuccess {
          log.info("Prisoner court cases reconciliation report completed with ${it.mismatches.size} mismatches")
          telemetryClient.trackEvent(
            "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-report",
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
          telemetryClient.trackEvent("$TELEMETRY_COURT_CASE_PRISONER_PREFIX-report", mapOf("success" to "false", "error" to (it.message ?: "unknown")))
          log.error("Prisoner court case reconciliation report failed", it)
        }
    }
  }
}
private fun List<MismatchPrisonerCasesResponse>.asPrisonerMap(): Map<String, String> = this.associate { it.offenderNo to "cases=${it.mismatches.size}" }
