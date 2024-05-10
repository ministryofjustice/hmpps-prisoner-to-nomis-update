package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@RestController
class AlertsResource(
  private val telemetryClient: TelemetryClient,
  private val alertsReconciliationService: AlertsReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/alerts/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateAlertsReconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("alerts-reports-reconciliation-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))
    log.info("Alerts reconciliation report requested for $activePrisonersCount active prisoners")

    reportScope.launch {
      runCatching { alertsReconciliationService.generateReconciliationReport(activePrisonersCount) }
        .onSuccess {
          log.info("Alerts reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent("alerts-reports-reconciliation-report", mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap())
        }
        .onFailure {
          telemetryClient.trackEvent("alerts-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("Alerts reconciliation report failed", it)
        }
    }
  }
}

private fun List<MismatchAlerts>.asMap(): Map<String, String> {
  return this.associate { it.offenderNo to ("missing-dps=${it.missingFromDps.size}:missing-nomis=${it.missingFromNomis.size}") }
}
