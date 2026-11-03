package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

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
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiMockServer.Companion.reconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationNomisApiMockServer.Companion.offenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

class PersonLocationReconciliationIntTest(
  @Autowired private val reconciliationService: PersonLocationReconciliationService,
  @Autowired private val nomisApi: PersonLocationNomisApiMockServer,
  @Autowired private val mappingApi: PersonLocationMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = dpsPersonLocationServer
  private val prisonerApi = NomisApiExtension.nomisApi

  @DisplayName("Generate reconciliation report")
  @Nested
  inner class GenerateReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      prisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      stubEmptyResponses()
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generatePersonLocationReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "1")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("pages-count", "1")
        },
        isNull(),
      )
    }

    @Test
    fun `will report failure to reconcile prisoner`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(offenderNo = "A0001TZ", status = INTERNAL_SERVER_ERROR)

      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-mismatch-error"),
        check {
          assertThat(it).containsEntry("offenderNo", "A0001TZ")
          assertThat(it).containsEntry("reason", "500 Internal Server Error from GET http://localhost:8082/movements/A0001TZ/person-locations")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class AdditionalEntities {

    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      prisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      stubEmptyResponses()
    }

    @Nested
    inner class AdditionalEntitiesInNomis {

      @BeforeEach
      fun `stub additional entities in NOMIS and run report`() = runTest {
        nomisApi.stubGetOffenderPersonLocations(
          offenderNo = "A0001TZ",
          response = offenderPersonLocationsResponse(),
        )

        reconciliationService.generatePersonLocationReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS booking`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "BOOKING",
              "nomisCount" to "1",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[12345]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS movements`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "MOVEMENT",
              "nomisCount" to "2",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[12345_1, 12345_2]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class AdditionalEntitiesInDps {
      private val dpsResponse = reconciliationResponse()
      private val seriesId = dpsResponse.series[0].series.dpsId
      private val movementInId = dpsResponse.series[0].movements[0].dpsId
      private val movementOutId = dpsResponse.series[0].movements[1].dpsId

      @BeforeEach
      fun `stub additional entities in DPS and run report`() = runTest {
        dpsApi.stubReconciliation(
          personIdentifier = "A0001TZ",
          response = dpsResponse,
        )

        reconciliationService.generatePersonLocationReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra DPS series`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "BOOKING",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$seriesId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS movements`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "MOVEMENT",
              "nomisCount" to "0",
              "dpsCount" to "2",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$movementInId, $movementOutId]",
            ),
          ),
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class MissingMappings {
    private val dpsResponse = reconciliationResponse()
    private val seriesId = dpsResponse.series[0].series.dpsId
    private val movementInId = dpsResponse.series[0].movements[0].dpsId
    private val movementOutId = dpsResponse.series[0].movements[1].dpsId

    @BeforeEach
    fun `stub entities in NOMIS and DPS but no mappings`() = runTest {
      reset(telemetryClient)
      prisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(),
      )
      dpsApi.stubReconciliation(
        personIdentifier = "A0001TZ",
        response = dpsResponse,
      )
      mappingApi.stubGetPersonLocationMappings(response = emptyList())

      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()
    }

    @Test
    fun `should report missing booking mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0001TZ",
            "type" to "MISSING_MAPPING_BOOKING",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[12345]",
            "unexpected-dps-ids" to "[$seriesId]",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `should report missing  movement mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0001TZ",
            "type" to "MISSING_MAPPING_MOVEMENT",
            "nomisCount" to "2",
            "dpsCount" to "2",
            "unexpected-nomis-ids" to "[12345_1, 12345_2]",
            "unexpected-dps-ids" to "[$movementInId, $movementOutId]",
          ),
        ),
        isNull(),
      )
    }
  }

  private fun stubEmptyResponses() {
    nomisApi.stubGetOffenderPersonLocations(
      offenderNo = "A0001TZ",
      response = offenderPersonLocationsResponse(bookings = emptyList()),
    )

    dpsApi.stubReconciliation(
      personIdentifier = "A0001TZ",
      response = reconciliationResponse(series = emptyList()),
    )

    mappingApi.stubGetPersonLocationMappings(response = emptyList())
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("person-location-reconciliation-report"), any(), isNull()) }
  }
}
