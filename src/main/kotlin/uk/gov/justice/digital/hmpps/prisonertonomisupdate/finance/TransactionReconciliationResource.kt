package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent

@RestController
class TransactionReconciliationResource(
  private val telemetryClient: TelemetryClient,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/transactions/reports/reconciliation")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun generateFinanceReconciliationReport() {
    telemetryClient.trackEvent(
      "transactions-reconciliation-requested-not-implemented",
      mapOf(),
    )
  }
}
