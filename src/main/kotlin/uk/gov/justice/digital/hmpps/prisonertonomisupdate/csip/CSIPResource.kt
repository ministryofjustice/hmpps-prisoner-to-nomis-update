package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

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
class CSIPResource(
  private val telemetryClient: TelemetryClient,
  private val csipReconciliationService: CSIPReconciliationService,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/csip/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateCSIPReconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("csip-reports-reconciliation-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))
    log.info("CSIP reconciliation report requested for $activePrisonersCount active prisoners")

    reportScope.launch {
      runCatching { csipReconciliationService.generateReconciliationReport(activePrisonersCount) }
        .onSuccess {
          log.info("CSIP reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent(
            "csip-reports-reconciliation-report",
            mapOf("mismatch-count" to it.size.toString(), "success" to "true"),
            // TODO ADD BACK IN + it.asMap(),
          )
        }
        .onFailure {
          telemetryClient.trackEvent("csip-reports-reconciliation-report", mapOf("success" to "false"))
          log.error("CSIP reconciliation report failed", it)
        }
    }
  }
}
