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
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.ACTIVE_FLAG
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_BEGIN_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_END_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_NUMBER
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_STATUS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.COMMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.DIRECTION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.FROM_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_REASON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TYPE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.TO_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiMockServer.Companion.reconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationNomisApiMockServer.Companion.offenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingPersonLocations
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationBooking.BookingStatus.CLOSED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.DirectionCode.IN
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.REL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.TAP
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, MOVEMENT_TYPE)
      verifyTelemetry("12345_2", movementOutId, MOVEMENT_TYPE)
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, DIRECTION)
      verifyTelemetry("12345_2", movementOutId, DIRECTION)
    }

    @Test
    fun `movement time different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          movements = listOf(
            validTransferIn.copy(movementTime = LocalDateTime.now()),
            validCourtOut.copy(movementTime = LocalDateTime.now()),
          ),
        ),
      )
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, MOVEMENT_TIME)
      verifyTelemetry("12345_2", movementOutId, MOVEMENT_TIME)
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, MOVEMENT_REASON)
      verifyTelemetry("12345_2", movementOutId, MOVEMENT_REASON)
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, FROM_LOCATION)
      verifyTelemetry("12345_2", movementOutId, FROM_LOCATION)
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, TO_LOCATION)
      verifyTelemetry("12345_2", movementOutId, TO_LOCATION)
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry("12345_1", movementInId, COMMENT)
      verifyTelemetry("12345_2", movementOutId, COMMENT)
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry(BOOKING_STATUS)
    }

    @Test
    fun `active flag different`() = runTest {
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry(ACTIVE_FLAG)
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
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry(BOOKING_NUMBER)
    }

    @Test
    fun `booking begin time different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          bookings = listOf(
            BookingPersonLocations(
              booking = validBooking.copy(bookingBeginTime = LocalDateTime.now()),
              movements = listOf(validTransferIn, validCourtOut),
            ),
          ),
        ),
      )
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry(BOOKING_BEGIN_TIME)
    }

    @Test
    fun `booking end time different`() = runTest {
      nomisApi.stubGetOffenderPersonLocations(
        offenderNo = "A0001TZ",
        response = offenderPersonLocationsResponse(
          bookings = listOf(
            BookingPersonLocations(
              booking = validBooking.copy(bookingEndTime = LocalDateTime.now()),
              movements = listOf(validTransferIn, validCourtOut),
            ),
          ),
        ),
      )
      reconciliationService.generatePersonLocationReconciliationReportBatch()
      awaitReportFinished()

      verifyTelemetry(BOOKING_END_TIME)
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
