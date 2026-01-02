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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitId

class OfficialVisitsAllReconciliationIntTest(
  @Autowired private val reconciliationService: OfficialVisitsAllReconciliationService,
  @Autowired private val nomisApi: OfficialVisitsNomisApiMockServer,
  @Autowired private val mappingApi: OfficialVisitsMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @DisplayName("Official visits all reconciliation report")
  @Nested
  inner class GenerateAllVisitsReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetOfficialVisitIds(content = listOf(VisitIdResponse(1), VisitIdResponse(2), VisitIdResponse(3)))
      nomisApi.stubGetOfficialVisitIdsByLastId(content = listOf(VisitIdResponse(1), VisitIdResponse(2), VisitIdResponse(3)))
      dpsApi.stubGetOfficialVisitIds(content = listOf(SyncOfficialVisitId(100)))

      mappingApi.stubGetByNomisIdsOrNull(
        nomisVisitId = 1,
        mapping = OfficialVisitMappingDto(
          dpsId = "100",
          nomisId = 1,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )
      nomisApi.stubGetOfficialVisit(visitId = 1, response = officialVisitResponse().copy(visitId = 1))
      dpsApi.stubGetOfficialVisit(officialVisitId = 100, response = syncOfficialVisit())

      mappingApi.stubGetByNomisIdsOrNull(
        nomisVisitId = 2,
        mapping = null,
      )
      nomisApi.stubGetOfficialVisit(visitId = 2, response = officialVisitResponse().copy(visitId = 2, offenderNo = "A1234KT"))

      mappingApi.stubGetByNomisIdsOrNull(
        nomisVisitId = 3,
        mapping = OfficialVisitMappingDto(
          dpsId = "300",
          nomisId = 3,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )
      nomisApi.stubGetOfficialVisit(visitId = 3, response = officialVisitResponse().copy(visitId = 3, offenderNo = "A4321KT"))
      dpsApi.stubGetOfficialVisit(officialVisitId = 300, response = null)
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
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("pages-count", "1")
          assertThat(it).containsEntry("visit-count", "3")
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
            "nomisTotal" to "3",
            "dpsTotal" to "1",
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
        eq("official-visits-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "nomisVisitId" to "2",
            "offenderNo" to "A1234KT",
            "reason" to "official-visit-mapping-missing",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch for missing DPS visit when mapping exists`() = runTest {
      reconciliationService.generateAllVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-all-reconciliation-mismatch"),
        eq(
          mapOf(
            "nomisVisitId" to "3",
            "dpsVisitId" to "300",
            "offenderNo" to "A4321KT",
            "reason" to "dps-record-missing",
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
