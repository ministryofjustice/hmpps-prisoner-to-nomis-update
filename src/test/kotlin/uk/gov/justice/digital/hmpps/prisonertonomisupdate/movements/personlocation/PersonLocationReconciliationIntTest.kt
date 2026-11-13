package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection.ACTIVE_PRISONERS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection.ALL_PRISONERS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.NomisMovementId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.ACTIVE_FLAG
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_BEGIN_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_END_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_NUMBER
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_STATUS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.BOOKING
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MISSING_MAPPING_BOOKING
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MISSING_MAPPING_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.COMMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.DIRECTION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.FROM_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_REASON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TYPE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.TO_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.ACTIVE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.IN_OUT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.PRISON_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiMockServer.Companion.activeSeries
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiMockServer.Companion.reconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationNomisApiMockServer.Companion.activeBooking
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationNomisApiMockServer.Companion.offenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingPersonLocations
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationBooking.BookingStatus.CLOSED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.DirectionCode.IN
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.REL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.ReconciliationPrisonerStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.ReconciliationSeries
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.time.LocalDateTime
import java.util.*

class PersonLocationReconciliationIntTest(
  @Autowired private val reconciliationService: PersonLocationReconciliationService,
  @Autowired private val nomisApi: PersonLocationNomisApiMockServer,
  @Autowired private val mappingApi: PersonLocationMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = dpsPersonLocationServer
  private val prisonerApi = NomisApiExtension.nomisApi

  @DisplayName("Generate reconciliation report - all prisoners")
  @Nested
  inner class GenerateReconciliationReportBatchAllPrisoners {
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
      reconciliationService.generateReport(ALL_PRISONERS)

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-requested"),
        check { assertThat(it).containsEntry("TYPE", "ALL_PRISONERS") },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-report"),
        check {
          assertThat(it).containsEntry("TYPE", "ALL_PRISONERS")
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

      reconciliationService.generateReport(ALL_PRISONERS)
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

  @DisplayName("Generate reconciliation report - active prisoners")
  @Nested
  inner class GenerateReconciliationReportBatchActivePrisoners {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      prisonerApi.stubGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(
          lastBookingId = 12345,
          prisonerIds = listOf(PrisonerIds(12345L, generateOffenderNo(sequence = 1))),
        ),
      )

      stubEmptyResponses()
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateReport(ACTIVE_PRISONERS)

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-requested"),
        check { assertThat(it).containsEntry("TYPE", "ACTIVE_PRISONERS") },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateReport(ACTIVE_PRISONERS)
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("person-location-reconciliation-report"),
        check {
          assertThat(it).containsEntry("TYPE", "ACTIVE_PRISONERS")
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

      reconciliationService.generateReport(ACTIVE_PRISONERS)
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

        reconciliationService.generateReport(ALL_PRISONERS)
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

        verifyReconciliationEndpointIds(
          type = BOOKING,
          nomisCount = 1,
          unexpectedNomisIds = "[12345]",
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

        verifyReconciliationEndpointIds(
          type = MOVEMENT,
          nomisCount = 2,
          unexpectedNomisIds = "[12345_1, 12345_2]",
        )
      }
    }

    @Nested
    inner class AdditionalEntitiesInDps {
      private val mappedSeriesId = UUID.randomUUID()
      private val extraDps = reconciliationResponse()
      private val dpsResponse = reconciliationResponse(
        series = listOf(
          ReconciliationSeries(series = activeSeries(id = mappedSeriesId, openedAt = LocalDateTime.parse("2024-01-01T10:00:00")), movements = emptyList()),
          extraDps.series[0],
        ),
      )
      private val seriesId = extraDps.series[0].series.dpsId
      private val movementInId = extraDps.series[0].movements[0].dpsId
      private val movementOutId = extraDps.series[0].movements[1].dpsId

      @BeforeEach
      fun `stub additional entities in DPS and run report`() = runTest {
        nomisApi.stubGetOffenderPersonLocations(
          offenderNo = "A0001TZ",
          response = offenderPersonLocationsResponse(bookings = listOf(activeBooking())),
        )
        mappingApi.stubGetPersonLocationMappings(
          response = listOf(PersonLocationBookingMappingDto(bookingId = 12345L, dpsCustodialSeriesId = mappedSeriesId, movements = emptyList())),
        )
        dpsApi.stubReconciliation(
          personIdentifier = "A0001TZ",
          response = dpsResponse,
        )

        reconciliationService.generateReport(ALL_PRISONERS)
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
              "nomisCount" to "1",
              "dpsCount" to "2",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$seriesId]",
            ),
          ),
          isNull(),
        )

        verifyReconciliationEndpointIds(
          type = BOOKING,
          nomisCount = 1,
          dpsCount = 2,
          unexpectedDpsIds = "[$seriesId]",
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

        verifyReconciliationEndpointIds(
          type = MOVEMENT,
          dpsCount = 2,
          unexpectedDpsIds = "[$movementInId, $movementOutId]",
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

      reconciliationService.generateReport(ALL_PRISONERS)
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

      verifyReconciliationEndpointIds(
        type = MISSING_MAPPING_BOOKING,
        nomisCount = 1,
        dpsCount = 1,
        unexpectedNomisIds = "[12345]",
        unexpectedDpsIds = "[$seriesId]",
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

      verifyReconciliationEndpointIds(
        type = MISSING_MAPPING_MOVEMENT,
        nomisCount = 2,
        dpsCount = 2,
        unexpectedNomisIds = "[12345_1, 12345_2]",
        unexpectedDpsIds = "[$movementInId, $movementOutId]",
      )
    }
  }

  @Nested
  inner class MovementProperties {
    private val dpsResponse = reconciliationResponse()
    private val seriesId = dpsResponse.series[0].series.dpsId!!
    private val movementInId = dpsResponse.series[0].movements[0].dpsId!!
    private val movementOutId = dpsResponse.series[0].movements[1].dpsId!!
    private val validTransferIn = offenderPersonLocationsResponse().bookings[0].movements[0]
    private val validCourtOut = offenderPersonLocationsResponse().bookings[0].movements[1]

    fun verifyTelemetry(
      nomisMovementId: String,
      dpsMovementId: UUID,
      type: MismatchedPrisonerLocationMovements.Type,
    ) = verify(telemetryClient).trackEvent(
      eq("person-location-reconciliation-mismatch"),
      eq(
        mapOf(
          "offenderNo" to "A0001TZ",
          "nomisMovementId" to nomisMovementId,
          "dpsMovementId" to "$dpsMovementId",
          "type" to type.name,
        ),
      ),
      isNull(),
    )

    @BeforeEach
    fun `stub matching entities in NOMIS and DPS`() = runTest {
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
      mappingApi.stubGetPersonLocationMappings(
        response = listOf(
          PersonLocationBookingMappingDto(
            bookingId = 12345L,
            dpsCustodialSeriesId = seriesId,
            movements = listOf(
              PersonLocationMovementMappingDto(
                nomisMovementSeq = 1,
                dpsExternalMovementId = movementInId,
              ),
              PersonLocationMovementMappingDto(
                nomisMovementSeq = 2,
                dpsExternalMovementId = movementOutId,
              ),
            ),
          ),
        ),
      )
    }

    @Test
    fun `should report nothing if no differences`() = runTest {
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verify(telemetryClient, never()).trackEvent(
        eq("person-location-reconciliation-mismatch"),
        any(),
        isNull(),
      )
    }

    @Test
    fun `movement type different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          movements = listOf(
            validTransferIn.copy(movementType = REL),
            validCourtOut.copy(movementType = TAP),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, MOVEMENT_TYPE)
      verifyTelemetry("12345_2", movementOutId, MOVEMENT_TYPE)

      verifyReconciliationEndpointMovement(
        type = MOVEMENT_TYPE,
        nomisMoveSeq = 1,
        dpsId = movementInId,
        nomisValue = "REL",
        dpsValue = "ADMISSION",
      )
      verifyReconciliationEndpointMovement(
        type = MOVEMENT_TYPE,
        nomisMoveSeq = 2,
        dpsId = movementOutId,
        nomisValue = "TAP",
        dpsValue = "COURT_APPEARANCE",
      )
    }

    @Test
    fun `direction code different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          movements = listOf(
            validTransferIn.copy(directionCode = OUT),
            validCourtOut.copy(directionCode = IN),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, DIRECTION)
      verifyTelemetry("12345_2", movementOutId, DIRECTION)

      verifyReconciliationEndpointMovement(
        type = DIRECTION,
        nomisMoveSeq = 1,
        dpsId = movementInId,
        nomisValue = "OUT",
        dpsValue = "ARRIVAL",
      )
      verifyReconciliationEndpointMovement(
        type = DIRECTION,
        nomisMoveSeq = 2,
        dpsId = movementOutId,
        nomisValue = "IN",
        dpsValue = "DEPARTURE",
      )
    }

    @Test
    fun `movement time different`() = runTest {
      val now = LocalDateTime.now()
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          movements = listOf(
            validTransferIn.copy(movementTime = now),
            validCourtOut.copy(movementTime = now),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, MOVEMENT_TIME)
      verifyTelemetry("12345_2", movementOutId, MOVEMENT_TIME)

      verifyReconciliationEndpointMovement(
        type = MOVEMENT_TIME,
        nomisMoveSeq = 1,
        dpsId = movementInId,
        nomisValue = "$now",
        dpsValue = "2024-01-01T10:00",
      )
      verifyReconciliationEndpointMovement(
        type = MOVEMENT_TIME,
        nomisMoveSeq = 2,
        dpsId = movementOutId,
        nomisValue = "$now",
        dpsValue = "2024-01-11T10:00",
      )
    }

    @Test
    fun `movement reason different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          movements = listOf(
            validTransferIn.copy(movementReason = CodeDescription("WRONG", "Wrong")),
            validCourtOut.copy(movementReason = CodeDescription("WRONG", "Wrong")),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, MOVEMENT_REASON)
      verifyTelemetry("12345_2", movementOutId, MOVEMENT_REASON)

      verifyReconciliationEndpointMovement(
        type = MOVEMENT_REASON,
        nomisMoveSeq = 1,
        dpsId = movementInId,
        nomisValue = "WRONG",
        dpsValue = "INT",
      )
      verifyReconciliationEndpointMovement(
        type = MOVEMENT_REASON,
        nomisMoveSeq = 2,
        dpsId = movementOutId,
        nomisValue = "WRONG",
        dpsValue = "CRT",
      )
    }

    @Test
    fun `from location different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          movements = listOf(
            validTransferIn.copy(from = MovementLocation(type = MovementLocation.Type.COURT, code = "WRONG", description = "Wrong")),
            validCourtOut.copy(from = MovementLocation(type = MovementLocation.Type.PRISON, code = "WRONG", description = "Wrong")),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, FROM_LOCATION)
      verifyTelemetry("12345_2", movementOutId, FROM_LOCATION)

      verifyReconciliationEndpointMovement(
        type = FROM_LOCATION,
        nomisMoveSeq = 1,
        dpsId = movementInId,
        nomisValue = "Wrong",
        dpsValue = "Sheffield Crown Court",
      )
      verifyReconciliationEndpointMovement(
        type = FROM_LOCATION,
        nomisMoveSeq = 2,
        dpsId = movementOutId,
        nomisValue = "Wrong",
        dpsValue = "Brixton",
      )
    }

    @Test
    fun `to location different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          movements = listOf(
            validTransferIn.copy(to = MovementLocation(type = MovementLocation.Type.PRISON, code = "WRONG", description = "Wrong")),
            validCourtOut.copy(to = MovementLocation(type = MovementLocation.Type.COURT, code = "WRONG", description = "Wrong")),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, TO_LOCATION)
      verifyTelemetry("12345_2", movementOutId, TO_LOCATION)

      verifyReconciliationEndpointMovement(
        type = TO_LOCATION,
        nomisMoveSeq = 1,
        dpsId = movementInId,
        nomisValue = "Wrong",
        dpsValue = "Brixton",
      )
      verifyReconciliationEndpointMovement(
        type = TO_LOCATION,
        nomisMoveSeq = 2,
        dpsId = movementOutId,
        nomisValue = "Wrong",
        dpsValue = "Leeds Crown Court",
      )
    }

    @Test
    fun `comment different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          movements = listOf(
            validTransferIn.copy(commentText = "WRONG"),
            validCourtOut.copy(commentText = "WRONG"),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, COMMENT)
      verifyTelemetry("12345_2", movementOutId, COMMENT)

      verifyReconciliationEndpointMovement(
        type = COMMENT,
        nomisMoveSeq = 1,
        dpsId = movementInId,
        nomisValue = "WRONG",
        dpsValue = "movement ADM notes",
      )
      verifyReconciliationEndpointMovement(
        type = COMMENT,
        nomisMoveSeq = 2,
        dpsId = movementOutId,
        nomisValue = "WRONG",
        dpsValue = "movement CRT notes",
      )
    }

    private fun verifyReconciliationEndpointMovement(
      offenderNo: String = "A0001TZ",
      type: MismatchedPrisonerLocationMovements.Type,
      nomisBookingId: Long = 12345,
      nomisMoveSeq: Int,
      dpsId: UUID,
      nomisValue: String,
      dpsValue: String,
    ) {
      verifyReconciliationEndpoint(
        MismatchedPrisonerLocationMovements(
          offenderNo = offenderNo,
          type = type,
          nomisMovementId = NomisMovementId(nomisBookingId, nomisMoveSeq),
          dpsMovementId = dpsId,
          nomisValue = nomisValue,
          dpsValue = dpsValue,
        ),
      )
    }
  }

  @Nested
  inner class BookingProperties {
    private val dpsResponse = reconciliationResponse()
    private val seriesId = dpsResponse.series[0].series.dpsId!!
    private val movementInId = dpsResponse.series[0].movements[0].dpsId!!
    private val movementOutId = dpsResponse.series[0].movements[1].dpsId!!

    private val validBooking = offenderPersonLocationsResponse().bookings[0].booking
    private val validTransferIn = offenderPersonLocationsResponse().bookings[0].movements[0]
    private val validCourtOut = offenderPersonLocationsResponse().bookings[0].movements[1]

    fun verifyTelemetry(
      type: MismatchedPrisonerLocationBookings.Type,
    ) = verify(telemetryClient).trackEvent(
      eq("person-location-reconciliation-mismatch"),
      eq(
        mapOf(
          "offenderNo" to "A0001TZ",
          "nomisBookingId" to "12345",
          "dpsSeriesId" to "$seriesId",
          "type" to type.name,
        ),
      ),
      isNull(),
    )

    @BeforeEach
    fun `stub matching entities in NOMIS and DPS`() = runTest {
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
      mappingApi.stubGetPersonLocationMappings(
        response = listOf(
          PersonLocationBookingMappingDto(
            bookingId = 12345L,
            dpsCustodialSeriesId = seriesId,
            movements = listOf(
              PersonLocationMovementMappingDto(
                nomisMovementSeq = 1,
                dpsExternalMovementId = movementInId,
              ),
              PersonLocationMovementMappingDto(
                nomisMovementSeq = 2,
                dpsExternalMovementId = movementOutId,
              ),
            ),
          ),
        ),
      )
    }

    @Test
    fun `should report nothing if no differences`() = runTest {
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verify(telemetryClient, never()).trackEvent(
        eq("person-location-reconciliation-mismatch"),
        any(),
        isNull(),
      )
    }

    @Test
    fun `booking status different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          bookings = listOf(
            BookingPersonLocations(
              booking = validBooking.copy(bookingStatus = CLOSED),
              movements = listOf(validTransferIn, validCourtOut),
            ),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry(BOOKING_STATUS)

      verifyReconciliationEndpointBooking(
        type = BOOKING_STATUS,
        nomisValue = "CLOSED",
        dpsValue = "OPEN",
      )
    }

    @Test
    fun `active flag different`() = runTest {
      dpsApi.stubReconciliation(
        personIdentifier = "A0001TZ",
        response = dpsResponse.copy(status = dpsResponse.status.copy(expectedLegacyActive = "INACTIVE")),
      )
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          bookings = listOf(
            BookingPersonLocations(
              booking = validBooking.copy(activeBooking = false),
              movements = listOf(validTransferIn, validCourtOut),
            ),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry(ACTIVE_FLAG)

      verifyReconciliationEndpointBooking(
        type = ACTIVE_FLAG,
        nomisValue = "false",
        dpsValue = "true",
      )
    }

    @Test
    fun `booking number different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          bookings = listOf(
            BookingPersonLocations(
              booking = validBooking.copy(bookingNumber = "WRONG"),
              movements = listOf(validTransferIn, validCourtOut),
            ),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry(BOOKING_NUMBER)

      verifyReconciliationEndpointBooking(
        type = BOOKING_NUMBER,
        nomisValue = "WRONG",
        dpsValue = "B12345",
      )
    }

    @Test
    fun `booking begin time different`() = runTest {
      val now = LocalDateTime.now()
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          bookings = listOf(
            BookingPersonLocations(
              booking = validBooking.copy(bookingBeginTime = now),
              movements = listOf(validTransferIn, validCourtOut),
            ),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry(BOOKING_BEGIN_TIME)

      verifyReconciliationEndpointBooking(
        type = BOOKING_BEGIN_TIME,
        nomisValue = "$now",
        dpsValue = "2024-01-01T10:00",
      )
    }

    @Test
    fun `booking end time different`() = runTest {
      val now = LocalDateTime.now()
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          bookings = listOf(
            BookingPersonLocations(
              booking = validBooking.copy(bookingEndTime = now),
              movements = listOf(validTransferIn, validCourtOut),
            ),
          ),
        ),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()

      verifyTelemetry(BOOKING_END_TIME)

      verifyReconciliationEndpointBooking(
        type = BOOKING_END_TIME,
        nomisValue = "$now",
        dpsValue = "null",
      )
    }

    private fun verifyReconciliationEndpointBooking(
      offenderNo: String = "A0001TZ",
      type: MismatchedPrisonerLocationBookings.Type,
      nomisBookingId: Long = 12345,
      dpsSeriesId: UUID = seriesId,
      nomisValue: String,
      dpsValue: String,
    ) {
      verifyReconciliationEndpoint(
        MismatchedPrisonerLocationBookings(
          offenderNo = offenderNo,
          type = type,
          nomisBookingId = nomisBookingId,
          dpsSeriesId = dpsSeriesId,
          nomisValue = nomisValue,
          dpsValue = dpsValue,
        ),
      )
    }
  }

  @Nested
  inner class PrisonerStatus {
    private val dpsResponse = reconciliationResponse()
    private val validDpsStatus = dpsResponse.status
    private val validBookingLocations = offenderPersonLocationsResponse().bookings[0]
    private val validBooking = validBookingLocations.booking

    fun verifyTelemetry(type: MismatchedPrisonerLocationStatus.Type, nomisValue: String, dpsValue: String, nomisBookingId: Long = 12345) = verify(telemetryClient).trackEvent(
      eq("person-location-reconciliation-mismatch"),
      eq(
        mapOf(
          "offenderNo" to "A0001TZ",
          "type" to type.name,
          "nomisBookingId" to "$nomisBookingId",
          "nomisValue" to nomisValue,
          "dpsValue" to dpsValue,
        ),
      ),
      isNull(),
    )

    fun verifyNoStatusTelemetry() = verify(telemetryClient, never()).trackEvent(
      eq("person-location-reconciliation-mismatch"),
      check { assertThat(it["type"]).isIn(ACTIVE.name, IN_OUT.name, PRISON_CODE.name) },
      isNull(),
    )

    fun stubAndRunReport(nomisBookings: List<BookingPersonLocations> = listOf(validBookingLocations), dpsStatus: ReconciliationPrisonerStatus = validDpsStatus) = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(bookings = nomisBookings),
      )
      dpsApi.stubReconciliation(
        personIdentifier = "A0001TZ",
        response = dpsResponse.copy(status = dpsStatus),
      )
      reconciliationService.generateReport(ALL_PRISONERS)
      awaitReportFinished()
    }

    @BeforeEach
    fun `stub prisoner and mappings`() {
      reset(telemetryClient)
      prisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )
      mappingApi.stubGetPersonLocationMappings(
        response = listOf(
          PersonLocationBookingMappingDto(
            bookingId = 12345L,
            dpsCustodialSeriesId = dpsResponse.series[0].series.dpsId!!,
            movements = dpsResponse.series[0].movements.mapIndexed { i, movement ->
              PersonLocationMovementMappingDto(nomisMovementSeq = i + 1, dpsExternalMovementId = movement.dpsId!!)
            },
          ),
        ),
      )
    }

    @Test
    fun `should report no status mismatches if matching`() {
      stubAndRunReport()

      verifyNoStatusTelemetry()
    }

    @Test
    fun `active different`() {
      stubAndRunReport(dpsStatus = validDpsStatus.copy(expectedLegacyActive = "INACTIVE"))

      verifyTelemetry(ACTIVE, nomisValue = "ACTIVE", dpsValue = "INACTIVE")
      verifyReconciliationEndpointStatus(ACTIVE, nomisValue = "ACTIVE", dpsValue = "INACTIVE")
    }

    @Test
    fun `in out different`() {
      stubAndRunReport(dpsStatus = validDpsStatus.copy(expectedLegacyInOut = "OUT"))

      verifyTelemetry(IN_OUT, nomisValue = "IN", dpsValue = "OUT")
      verifyReconciliationEndpointStatus(IN_OUT, nomisValue = "IN", dpsValue = "OUT")
    }

    @Test
    fun `prison code different`() {
      stubAndRunReport(dpsStatus = validDpsStatus.copy(prisonCode = "MDI"))

      verifyTelemetry(PRISON_CODE, nomisValue = "BXI", dpsValue = "MDI")
      verifyReconciliationEndpointStatus(PRISON_CODE, nomisValue = "BXI", dpsValue = "MDI")
    }

    @ParameterizedTest
    @ValueSource(strings = ["OUT", "TRN"])
    fun `should expect no DPS prison code when NOMIS prison is not a prison`(nomisPrison: String) {
      stubAndRunReport(
        nomisBookings = listOf(validBookingLocations.copy(booking = validBooking.copy(prison = nomisPrison))),
        dpsStatus = validDpsStatus.copy(prisonCode = null),
      )

      verifyNoStatusTelemetry()
    }

    @ParameterizedTest
    @ValueSource(strings = ["OUT", "TRN"])
    fun `should report DPS prison code when NOMIS prison is not a prison`(nomisPrison: String) {
      stubAndRunReport(
        nomisBookings = listOf(validBookingLocations.copy(booking = validBooking.copy(prison = nomisPrison))),
      )

      verifyTelemetry(PRISON_CODE, nomisValue = nomisPrison, dpsValue = "BXI")
    }

    @Test
    fun `should use the active booking rather than an older inactive booking`() {
      val oldBooking = validBooking.copy(bookingId = 1, activeBooking = false, latestBooking = false, prison = "OUT", inOutStatus = "OUT")
      stubAndRunReport(
        nomisBookings = listOf(
          BookingPersonLocations(booking = oldBooking, movements = emptyList()),
          BookingPersonLocations(booking = validBooking, movements = emptyList()),
        ),
      )

      verifyNoStatusTelemetry()
    }

    @Test
    fun `should use the latest booking when no booking is active`() {
      val oldBooking = validBooking.copy(bookingId = 1, activeBooking = false, latestBooking = false)
      val latestBooking = validBooking.copy(bookingId = 2, activeBooking = false, prison = "OUT", inOutStatus = "OUT")
      stubAndRunReport(
        nomisBookings = listOf(
          BookingPersonLocations(booking = oldBooking, movements = emptyList()),
          BookingPersonLocations(booking = latestBooking, movements = emptyList()),
        ),
        dpsStatus = validDpsStatus.copy(expectedLegacyActive = "ACTIVE", expectedLegacyInOut = "IN"),
      )

      verifyTelemetry(ACTIVE, nomisValue = "INACTIVE", dpsValue = "ACTIVE", nomisBookingId = 2)
      verifyTelemetry(IN_OUT, nomisValue = "OUT", dpsValue = "IN", nomisBookingId = 2)
      verifyTelemetry(PRISON_CODE, nomisValue = "OUT", dpsValue = "BXI", nomisBookingId = 2)
    }

    private fun verifyReconciliationEndpointStatus(
      type: MismatchedPrisonerLocationStatus.Type,
      nomisValue: String,
      dpsValue: String,
    ) {
      verifyReconciliationEndpoint(
        MismatchedPrisonerLocationStatus(
          offenderNo = "A0001TZ",
          type = type,
          nomisBookingId = 12345,
          nomisValue = nomisValue,
          dpsValue = dpsValue,
        ),
      )
    }
  }

  @Nested
  inner class ReconciliationEndpoint {

    @Nested
    inner class Validation {
      @Test
      fun `should return error for unknown offender`() {
        webTestClient.get().uri("/external-movements/person-locations/UNKNOWN/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().is5xxServerError
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/external-movements/person-locations/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/external-movements/person-locations/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/external-movements/person-locations/A0001TZ/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
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

  private fun verifyReconciliationEndpointIds(
    offenderNo: String = "A0001TZ",
    type: MismatchedPrisonerLocationIds.Type,
    nomisCount: Int = 0,
    dpsCount: Int = 0,
    unexpectedNomisIds: String = "[]",
    unexpectedDpsIds: String = "[]",
  ) {
    verifyReconciliationEndpoint(
      MismatchedPrisonerLocationIds(
        offenderNo = offenderNo,
        type = type,
        nomisCount = nomisCount,
        dpsCount = dpsCount,
        unexpectedNomisIds = unexpectedNomisIds,
        unexpectedDpsIds = unexpectedDpsIds,
      ),
    )
  }

  private fun verifyReconciliationEndpoint(
    mismatch: MismatchedPrisonerLocations,
  ) {
    reset(telemetryClient)

    webTestClient.get()
      .uri("/external-movements/person-locations/${mismatch.offenderNo}/reconciliation")
      .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
      .exchange()
      .expectStatus().isOk()
      .expectBodyList(mismatch.javaClass)
      .returnResult().responseBody!!
      .apply {
        assertThat(this)
          .withFailMessage(
            """
            Expected mismatch does not match any actual response:
            $mismatch
            $this
            """.trimIndent(),
          )
          .usingRecursiveFieldByFieldElementComparator()
          .contains(mismatch)
      }

    verify(telemetryClient, never()).trackEvent(any(), any(), any())
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("person-location-reconciliation-report"), any(), isNull()) }
  }
}
