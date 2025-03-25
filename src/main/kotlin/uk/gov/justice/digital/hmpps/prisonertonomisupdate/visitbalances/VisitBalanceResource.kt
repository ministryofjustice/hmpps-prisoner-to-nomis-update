package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

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
class VisitBalanceResource(
  private val telemetryClient: TelemetryClient,
  private val reconciliationService: VisitBalanceReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/visit-balance/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateReconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("visitbalance-reports-reconciliation-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))
    log.info("Visit Balance reconciliation report requested for $activePrisonersCount active prisoners")

    reportScope.launch {
      runCatching { reconciliationService.generateReconciliationReport(activePrisonersCount) }
        .onSuccess {
          log.info("Visit balance reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent("visitbalance-reports-reconciliation-report", mapOf("mismatch-count" to it.size.toString(), "success" to "true"))
        }
        .onFailure {
          telemetryClient.trackEvent("visitbalance-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("Visit balance reconciliation report failed", it)
        }
    }
  }
}
