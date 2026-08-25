package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerGetResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDpsApiExtension.Companion.propertyDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PageUUID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PropertyContainerDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import java.util.UUID

class PropertyReconciliationResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var propertyReconciliationService: PropertyReconciliationService

  @Autowired
  private lateinit var propertyNomisApi: PropertyNomisApiMockServer

  @Autowired
  private lateinit var propertyMappingApiMockServer: PropertyMappingApiMockServer

  @DisplayName("Property reconciliation report")
  @Nested
  inner class GeneratePropertyReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      propertyNomisApi.stubGetPropertyIdRanges(
        pageSize = 10,
        totalElements = 20,
      )
      propertyNomisApi.stubGetPropertyIdsInRange(
        fromId = 0,
        toId = 10,
      )
      propertyNomisApi.stubGetPropertyIdsInRange(
        fromId = 10,
        toId = 20,
      )
      propertyNomisApi.stubGetPropertyIdsInRange(
        fromId = 20,
        toId = -1, // end
      )
      for (i in 1..20L) {
        stubProperty(
          i,
          nomisProperty().copy(offenderNo = "A000${i}NN"),
          dpsProperty().copy(prisonerNumber = "A000${i}NN"),
        )
      }
      propertyDpsApi.stubGetAllIds(page = 0, size = 6, PageUUID(content = (1L..6L).map { UUID.fromString(generateDpsId(it)) }, propertySize = 6))
      propertyDpsApi.stubGetAllIds(page = 1, size = 6, PageUUID(content = (7L..12L).map { UUID.fromString(generateDpsId(it)) }, propertySize = 6))
      propertyDpsApi.stubGetAllIds(page = 2, size = 6, PageUUID(content = (13L..18L).map { UUID.fromString(generateDpsId(it)) }, propertySize = 6))
      propertyDpsApi.stubGetAllIds(page = 3, size = 6, PageUUID(content = (19L..20L).map { UUID.fromString(generateDpsId(it)) }, propertySize = 2))
    }

    @Test
    fun `will output report telemetry`() = runTest {
      propertyReconciliationService.generatePropertyReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("property-reports-reconciliation-requested"),
        any(),
        isNull(),
      )

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("property-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("nomis-property-count", "20")
            assertThat(it).containsEntry("dps-only-count", "0")
            assertThat(it).containsEntry("page-count", "2")
            assertThat(it).containsEntry("mismatch-count", "0")
            assertThat(it).containsEntry("success", "true")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `will output a mismatch when there is a difference in the DPS record`() = runTest {
      stubProperty(
        2,
        nomisProperty().copy(offenderNo = "A0002NN"),
        dpsProperty().copy(prisonerNumber = "OTHER"),
      )
      propertyReconciliationService.generatePropertyReconciliationReportBatch()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("property-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("nomis-property-count", "20")
          assertThat(it).containsEntry("dps-only-count", "0")
          assertThat(it).containsEntry("page-count", "2")
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there is a DPS record with no matching Nomis record`() = runTest {
      // 1 extra DPS record 21 with no matching Nomis record
      propertyDpsApi.stubGetAllIds(page = 3, size = 6, PageUUID(content = (19L..21L).map { UUID.fromString(generateDpsId(it)) }, propertySize = 3))

      propertyReconciliationService.generatePropertyReconciliationReportBatch()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("property-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("nomis-property-count", "20")
          assertThat(it).containsEntry("dps-only-count", "1")
          assertThat(it).containsEntry("page-count", "2")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("property-reports-reconciliation-report"),
          any(),
          isNull(),
        )
      }
    }
  }

  private fun stubProperty(nomisId: Long, nomisResponse: PropertyContainerGetResponse, dpsResponse: PropertyContainerDto) {
    propertyNomisApi.stubGetProperty(nomisId, nomisResponse)
    val dpsId = generateDpsId(nomisId)
    propertyMappingApiMockServer.stubGetByNomisId(
      nomisId,
      PropertyContainerMappingDto(dpsId, nomisId, BOOKING_ID, OFFENDER_NO, PropertyContainerMappingDto.MappingType.MIGRATED),
    )
    mappingServer.stubGetMappingGivenNomisLocationIdUsingExternalApi(
      NOMIS_LOCATION_ID,
      """{
          "dpsLocationId": "$DPS_LOCATION_ID",
          "nomisLocationId": $NOMIS_LOCATION_ID,
          "mappingType": "MIGRATED"
        }
        """,
    )
    propertyDpsApi.stubGetProperty(dpsId, dpsResponse.copy(id = UUID.fromString(dpsId)))
  }

  private fun generateDpsId(nomisId: Long): String = "be1ee367-8cfa-4499-942b-393811110${nomisId.toString().padStart(3, '0')}"
}
