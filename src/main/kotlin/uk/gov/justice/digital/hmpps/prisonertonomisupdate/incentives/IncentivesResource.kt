package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent

@RestController
class IncentivesResource(private val telemetryClient: TelemetryClient) {
  @PutMapping("/incentives/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun generateIncentiveReconciliationReport() {
    telemetryClient.trackEvent("incentives-reports-reconciliation-requested")
    telemetryClient.trackEvent("incentives-reports-reconciliation-report", mapOf("status" to "success", "issues-count" to "0"))
  }
}
