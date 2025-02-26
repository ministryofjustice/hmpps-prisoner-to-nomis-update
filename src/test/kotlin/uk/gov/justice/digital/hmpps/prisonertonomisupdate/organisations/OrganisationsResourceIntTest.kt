package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase

class OrganisationsResourceIntTest : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @Autowired
  private lateinit var nomisApi: OrganisationsNomisApiMockServer

  @DisplayName("PUT /organisations/reports/reconciliation")
  @Nested
  inner class GenerateOrganisationsReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetCorporateOrganisationIds(count = 1)
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/organisations/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(eq("organisations-reports-reconciliation-requested"), check { assertThat(it).containsEntry("organisations", "1") }, isNull())

      awaitReportFinished()
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("organisations-reports-reconciliation-report"), any(), isNull()) }
  }
}
