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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitId

class OfficialVisitsAllMissingFromNOMISReconciliationIntTest(
  @Autowired private val reconciliationService: OfficialVisitsAllMissingFromNOMISReconciliationService,
  @Autowired private val nomisApi: OfficialVisitsNomisApiMockServer,
  @Autowired private val mappingApi: OfficialVisitsMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @DisplayName("Official visits all missing from NOMIS reconciliation report")
  @Nested
  inner class GenerateAllVisitsReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetOfficialVisitIds(content = listOf(VisitIdResponse(1), VisitIdResponse(2), VisitIdResponse(3)))
      dpsApi.stubGetOfficialVisitIds(content = listOf(SyncOfficialVisitId(101), SyncOfficialVisitId(102), SyncOfficialVisitId(103), SyncOfficialVisitId(104), SyncOfficialVisitId(105)))
      dpsApi.stubGetOfficialVisitIds(pageNumber = 0, pageSize = 2, content = listOf(SyncOfficialVisitId(101), SyncOfficialVisitId(102)))
      dpsApi.stubGetOfficialVisitIds(pageNumber = 1, pageSize = 2, content = listOf(SyncOfficialVisitId(103), SyncOfficialVisitId(104)))
      dpsApi.stubGetOfficialVisitIds(pageNumber = 2, pageSize = 2, content = listOf(SyncOfficialVisitId(105)))

      mappingApi.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = 101,
        mapping = OfficialVisitMappingDto(
          dpsId = "101",
          nomisId = 1,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )
      nomisApi.stubGetOfficialVisit(visitId = 1, response = officialVisitResponse().copy(visitId = 1))

      mappingApi.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = 102,
        mapping = OfficialVisitMappingDto(
          dpsId = "102",
          nomisId = 2,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )
      nomisApi.stubGetOfficialVisit(visitId = 2, response = officialVisitResponse().copy(visitId = 2))

      mappingApi.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = 103,
        mapping = OfficialVisitMappingDto(
          dpsId = "103",
          nomisId = 3,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )
      nomisApi.stubGetOfficialVisit(visitId = 3, response = officialVisitResponse().copy(visitId = 3))

      mappingApi.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = 104,
        mapping = OfficialVisitMappingDto(
          dpsId = "104",
          nomisId = 4,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )
      nomisApi.stubGetOfficialVisitOrNull(visitId = 4, response = null)

      mappingApi.stubGetVisitByDpsIdsOrNull(
        dpsVisitId = 105,
        mapping = null,
      )
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateAllVisitsReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("official-visits-all-nomis-missing-reconciliation-requested"),
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
        eq("official-visits-all-nomis-missing-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("pages-count", "3")
          assertThat(it).containsEntry("visit-count", "5")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch of totals`() = runTest {
      reconciliationService.generateAllVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-all-nomis-missing-reconciliation-mismatch-totals"),
        eq(
          mapOf(
            "nomisTotal" to "3",
            "dpsTotal" to "5",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch for missing NOMIS visit when mapping exists`() = runTest {
      reconciliationService.generateAllVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-all-nomis-missing-reconciliation-mismatch"),
        eq(
          mapOf(
            "dpsVisitId" to "104",
            "nomisVisitId" to "4",
            "reason" to "nomis-record-missing",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch for missing mapping`() = runTest {
      reconciliationService.generateAllVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-all-nomis-missing-reconciliation-mismatch"),
        eq(
          mapOf(
            "dpsVisitId" to "105",
            "reason" to "official-visit-mapping-missing",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("official-visits-all-nomis-missing-reconciliation-report"), any(), isNull()) }
    }
  }
}
