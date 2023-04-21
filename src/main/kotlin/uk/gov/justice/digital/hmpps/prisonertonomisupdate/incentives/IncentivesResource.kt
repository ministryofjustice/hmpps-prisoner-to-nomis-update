package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
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
class IncentivesResource(
  private val telemetryClient: TelemetryClient,
  private val incentivesReconciliationService: IncentivesReconciliationService,
  private val nomisApiService: NomisApiService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @OptIn(DelicateCoroutinesApi::class)
  @PutMapping("/incentives/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateIncentiveReconciliationReport() {
    val activePrisonersCount = nomisApiService.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent("incentives-reports-reconciliation-requested", mapOf("active-prisoners" to activePrisonersCount.toString()))
    log.info("Incentives reconciliation report requested for $activePrisonersCount active prisoners")

    GlobalScope.launch {
      runCatching {
        incentivesReconciliationService.generateReconciliationReport(activePrisonersCount).also {
          log.info("Incentives reconciliation report completed with ${it.size} mismatches")
          telemetryClient.trackEvent("incentives-reports-reconciliation-report", mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap())
        }
      }.onFailure {
        log.error("Incentives reconciliation report failed", it)
        telemetryClient.trackEvent("incentives-reports-reconciliation-report", mapOf("mismatch-count" to "0", "success" to "false"))
      }
    }
  }
}

private fun List<MismatchIncentiveLevel>.asMap(): Map<String, String> {
  return this.associate { it.prisonerId.offenderNo to ("${it.nomisIncentiveLevel}:${it.dpsIncentiveLevel}") }
}
