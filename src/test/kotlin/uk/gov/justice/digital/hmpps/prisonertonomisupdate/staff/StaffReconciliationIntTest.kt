package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.StaffMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffIdResponse

class StaffReconciliationIntTest(
  @Autowired private val reconciliationService: StaffReconciliationService,
  @Autowired private val nomisApi: StaffNomisApiMockServer,
  @Autowired private val mappingApi: StaffMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = StaffDpsApiExtension.dpsStaffServer

  @DisplayName("Staff reconciliation report")
  @Nested
  inner class GenerateStaffReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetStaffIds(content = listOf(StaffIdResponse(1), StaffIdResponse(2), StaffIdResponse(3)))
      nomisApi.stubGetStaffIdsFromId(content = listOf(StaffIdResponse(1), StaffIdResponse(2), StaffIdResponse(3)))
      // dpsApi.stubGetStaffIds(content = listOf(DpsStaffId("100")))

      // staffId 1 - matches
      mappingApi.stubGetStaffByNomisIdOrNull(
        nomisStaffId = 1,
        mapping = StaffMappingDto(
          dpsId = "100",
          nomisId = 1,
          mappingType = StaffMappingDto.MappingType.MIGRATED,
        ),
      )
      nomisApi.stubGetStaffById(staffId = 1)
      dpsApi.stubGetStaff(nomisStaffId = 1)

      // staffId 2 - missing mapping
      mappingApi.stubGetStaffByNomisIdOrNull(
        nomisStaffId = 2,
        mapping = null,
      )
      nomisApi.stubGetStaffById(staffId = 2)

      // staffId 3 - missing from Dps
      mappingApi.stubGetStaffByNomisIdOrNull(
        nomisStaffId = 3,
        mapping = StaffMappingDto(
          dpsId = "300",
          nomisId = 3,
          mappingType = StaffMappingDto.MappingType.MIGRATED,
        ),
      )
      nomisApi.stubGetStaffById(staffId = 3)
      dpsApi.stubGetStaff(nomisStaffId = 3, response = null)
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("staff-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("staff-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("pages-count", "1")
          assertThat(it).containsEntry("staff-count", "3")
        },
        isNull(),
      )
    }

    /*
    TODO add back in if reconcile totals
    @Test
    fun `will output a mismatch of totals`() = runTest {
      reconciliationService.generateReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("staff-reconciliation-mismatch-totals"),
        eq(
          mapOf(
            "nomisTotal" to "3",
            "dpsTotal" to "1",
          ),
        ),
        isNull(),
      )
    }
     */

    @Test
    fun `will output a mismatch for missing mapping`() = runTest {
      reconciliationService.generateReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("staff-reconciliation-mismatch"),
        eq(
          mapOf(
            "nomisStaffId" to "2",
            "reason" to "staff-mapping-missing",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch for missing DPS staff when mapping exists`() = runTest {
      reconciliationService.generateReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("staff-reconciliation-mismatch"),
        eq(
          mapOf(
            "nomisStaffId" to "3",
            "dpsStaffId" to "300",
            "reason" to "dps-record-missing",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("staff-reconciliation-report"), any(), isNull()) }
    }
  }
}
