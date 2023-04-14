package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase

class IncentivesResourceIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var telemetryClient: TelemetryClient

  @DisplayName("PUT /incentives/reports/reconciliation")
  @Nested
  inner class GenerateIncentiveReconciliationReport {
    @Test
    fun `should return 202 and does not require a JWT token since it is protected by Ingress`() {
      webTestClient.put().uri("/incentives/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/incentives/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      telemetryClient.trackEvent("incentives-reports-reconciliation-requested")
    }
  }
}
