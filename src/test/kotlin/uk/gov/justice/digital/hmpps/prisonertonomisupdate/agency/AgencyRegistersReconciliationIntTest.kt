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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyId as nomisAgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyIdsResponse as nomisAgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.agencyId as dpsAgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.agencyIdsResponse as dpsAgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.AgencyIdsResponse as DpsAgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyIdsResponse as NomisAgencyIdsResponse

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
      nomisApi.stubGetAgencyIds(nomisAgencyIdsResponse("SHEFCC", "SHEFMC"))
      dpsApi.stubGetAgencyIds(dpsAgencyIdsResponse("SHEFCC", "SHEFMC", "SHEFYC"))

      nomisApi.stubGetAgency("SHEFCC", agencyResponse().copy(agencyId = "SHEFCC", description = "Sheffield Crown Court"))
      nomisApi.stubGetAgency("SHEFMC", agencyResponse().copy(agencyId = "SHEFMC", description = "Sheffield Magistrates Court"))
      dpsApi.stubGetAgency("SHEFCC", legacyAgencyDto().copy(name = "Sheffield Crown Court"))
      dpsApi.stubGetAgency("SHEFMC", legacyAgencyDto().copy(name = "Sheffield Magistrates Court"))
      dpsApi.stubGetAgency("SHEFYC", legacyAgencyDto().copy(name = "Sheffield Youth Court"))
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
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("agency-count", "3")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a missing agency`() = runTest {
      reconciliationService.generateAgencyReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("agency-reconciliation-mismatch"),
        eq(
          mapOf(
            "agencyId" to "SHEFYC",
            "reason" to "Missing in NOMIS",
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

private fun dpsAgencyIdsResponse(vararg agencyIds: String): DpsAgencyIdsResponse = dpsAgencyIdsResponse().copy(agencyIds = agencyIds.map { dpsAgencyId().copy(agencyId = it) })
private fun nomisAgencyIdsResponse(vararg agencyIds: String): NomisAgencyIdsResponse = nomisAgencyIdsResponse().copy(agencyIds = agencyIds.map { nomisAgencyId().copy(agencyId = it) })
