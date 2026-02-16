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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ActivePrison
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ActivePrisonWithTimeSlotResponse

class VisitSlotsReconciliationIntTest(
  @Autowired private val reconciliationService: VisitSlotsReconciliationService,
  @Autowired private val nomisApi: VisitSlotsNomisApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @DisplayName("Visit Slots reconciliation report")
  @Nested
  inner class GenerateVisitSlotsReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetActivePrisonsWithTimeSlots(ActivePrisonWithTimeSlotResponse(prisons = listOf(ActivePrison("BXI"), ActivePrison("MDI"))))
      nomisApi.stubGetTimeSlotsForPrison("BXI")
      nomisApi.stubGetTimeSlotsForPrison("MDI")
      dpsApi.stubGetTimeSlotsForPrison("BXI")
      dpsApi.stubGetTimeSlotsForPrison("MDI")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateVisitSlotsReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("visit-slots-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateVisitSlotsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("visit-slots-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("prisons-checked", "2")
          assertThat(it).containsEntry("mismatches", "false")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("visit-slots-reconciliation-report"), any(), isNull()) }
    }
  }
}
