package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

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
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitId

class OfficialVisitsAllReconciliationIntTest(
  @Autowired private val reconciliationService: OfficialVisitsAllReconciliationService,
  @Autowired private val nomisApi: OfficialVisitsNomisApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @DisplayName("Official visits all reconciliation report")
  @Nested
  inner class GenerateAllVisitsReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetOfficialVisitIds(content = listOf(VisitIdResponse(1L), VisitIdResponse(2L)))
      dpsApi.stubGetOfficialVisitIds(content = listOf(SyncOfficialVisitId(100L)))
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateAllVisitsReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("official-visits-all-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateAllVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-all-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("pages-count", "0")
          assertThat(it).containsEntry("visit-count", "0")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch of totals`() = runTest {
      reconciliationService.generateAllVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-all-reconciliation-mismatch-totals"),
        eq(
          mapOf(
            "nomisTotal" to "2",
            "dpsTotal" to "1",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("official-visits-all-reconciliation-report"), any(), isNull()) }
    }
  }
}
