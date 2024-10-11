package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

class CaseNotesResourceIntTest : IntegrationTestBase() {

  @SpyBean
  private lateinit var caseNotesReconciliationService: CaseNotesReconciliationService

  @DisplayName("PUT /casenotes/reports/reconciliation")
  @Nested
  inner class GenerateReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
    }

    @Test
    fun `will output report success telemetry`() {
      nomisApi.stubGetAllPrisonersInitialCount(1, 1)
      nomisApi.stubGetAllPrisonersPage(0, 0, 0)

      webTestClient.put().uri("/casenotes/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-requested"),
        check {
          assertThat(it).containsEntry("casenotes-nomis-total", "1")
        },
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }

    @Test
    fun `will output report failure telemetry`() = runTest {
      nomisApi.stubGetAllPrisonersInitialCount(1, 1)
      whenever(caseNotesReconciliationService.generateReconciliationReport(1)).thenThrow(RuntimeException("test"))

      webTestClient.put().uri("/casenotes/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-requested"),
        check {
          assertThat(it).containsEntry("casenotes-nomis-total", "1")
        },
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("success", "false")
          assertThat(it).containsEntry("error", "test")
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("casenotes-reports-reconciliation-report"), any(), isNull()) }
  }
}
