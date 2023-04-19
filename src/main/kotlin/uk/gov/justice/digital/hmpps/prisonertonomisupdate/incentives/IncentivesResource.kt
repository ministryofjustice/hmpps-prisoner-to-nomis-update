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

@RestController
class IncentivesResource(private val telemetryClient: TelemetryClient, private val incentivesReconciliationService: IncentivesReconciliationService) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @OptIn(DelicateCoroutinesApi::class)
  @PutMapping("/incentives/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun generateIncentiveReconciliationReport() {
    telemetryClient.trackEvent("incentives-reports-reconciliation-requested")
    log.info("Incentives reconciliation report requested")
    GlobalScope.launch {
      incentivesReconciliationService.generateReconciliationReport().also {
        log.info("Incentives reconciliation report completed with ${it.size} mismatches")
        telemetryClient.trackEvent("incentives-reports-reconciliation-report", mapOf("mismatch-count" to it.size.toString()) + it.asMap())
      }
    }
  }
}

private fun List<MismatchIncentiveLevel>.asMap(): Map<String, String> {
  return this.associate { it.prisonerId.offenderNo to ("${it.nomisIncentiveLevel}:${it.dpsIncentiveLevel}") }
}
