package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyId as nomisAgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyIdsResponse as nomisAgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.agencyId as dpsAgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.agencyIdsResponse as dpsAgencyIdsResponse

class AgencyRegistersReconciliationIntTest(
  @Autowired private val reconciliationService: AgencyRegistersReconciliationService,
  @Autowired private val nomisApi: AgencyNomisApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = AgencyRegistersDpsApiExtension.agencyRegistersApi

  @DisplayName("Agency reconciliation report")
  @Nested
  inner class GenerateAgencyReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetAgencyIds(nomisAgencyIdsResponse().copy(agencyIds = [nomisAgencyId().copy(agencyId = "SHEFCC"), nomisAgencyId().copy(agencyId = "SHEFMC")]))
      dpsApi.stubGetAgencyIds(dpsAgencyIdsResponse().copy(agencyIds = [dpsAgencyId().copy(agencyId = "SHEFCC"), dpsAgencyId().copy(agencyId = "SHEFMC"), dpsAgencyId().copy(agencyId = "SHEFYC")]))
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateAgencyReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("agency-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateAgencyReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("agency-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("agency-count", "0")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch of totals`() = runTest {
      reconciliationService.generateAgencyReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("agency-reconciliation-mismatch-totals"),
        eq(
          mapOf(
            "nomisTotal" to "2",
            "dpsTotal" to "3",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("agency-reconciliation-report"), any(), isNull()) }
    }
  }
}
