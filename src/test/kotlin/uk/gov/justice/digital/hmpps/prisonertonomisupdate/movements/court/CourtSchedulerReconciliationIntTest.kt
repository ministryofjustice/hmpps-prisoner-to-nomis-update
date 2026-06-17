package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.CourtEventMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.ReconciliationCourtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.NomisMovementId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiExtension.Companion.courtSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiMockServer.Companion.courtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiMockServer.Companion.courtEventMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiMockServer.Companion.reconciliation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtMovementIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtSchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.offenderCourtMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtMovementIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.time.LocalDateTime
import java.util.*

class CourtSchedulerReconciliationIntTest(
  @Autowired private val reconciliationService: CourtSchedulerReconciliationServiceAllPrisoners,
  @Autowired private val courtScheduleNomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
  @Autowired private val courtSentencingMappingApi: CourtSentencingMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = courtSchedulerDpsApiServer
  private val nomisApi = NomisApiExtension.nomisApi

  @DisplayName("Generate reconciliation report")
  @Nested
  inner class GenerateReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      courtScheduleNomisApi.stubGetOffenderCourtMovements(offenderNo = "A0001TZ")
      dpsApi.stubGetCourtSchedulerReconciliation(personIdentifier = "A0001TZ")
      mappingApi.stubGetCourtSchedulerPrisonerMappingIds(prisonerNumber = "A0001TZ")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateCourtSchedulerReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("court-scheduler-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateCourtSchedulerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-scheduler-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "1")
          assertThat(it).containsEntry("pages-count", "1")
        },
        isNull(),
      )
    }

    @Test
    fun `will report failure to reconcile prisoner`() = runTest {
      courtScheduleNomisApi.stubGetOffenderCourtMovements(status = INTERNAL_SERVER_ERROR)

      reconciliationService.generateCourtSchedulerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("court-scheduler-reconciliation-mismatch-error"),
        check {
          assertThat(it).containsEntry("offenderNo", "A0001TZ")
          assertThat(it).containsEntry("reason", "500 Internal Server Error from GET http://localhost:8082/movements/A0001TZ/court")
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
      nomisApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      stubEmptyResponses()
    }

    @Nested
    inner class AdditionalEntitiesInNomis {

      @BeforeEach
      fun `stub additional NOMIS entities and run report`() = runTest {
        courtScheduleNomisApi.stubGetOffenderCourtMovements(
          offenderNo = "A0001TZ",
          response = offenderCourtMovementsResponse(
            courtSchedules = listOf(
              bookingCourtSchedule(
                eventId = 123,
                courtMovementOut = bookingCourtMovementOut(seq = 456),
                courtMovementIn = bookingCourtMovementIn(seq = 789),
              ),
            ),
            unscheduledCourtMovementOuts = listOf(bookingCourtMovementOut(seq = 654)),
            unscheduledCourtMovementIns = listOf(bookingCourtMovementIn(seq = 987)),
          ),
        )

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS court event`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULE",
              "nomisCount" to "1",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[123]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS court movements OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_MOVEMENT_OUT",
              "nomisCount" to "1",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[12345_456]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra unscheduled NOMIS court movements OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_MOVEMENT_OUT",
              "nomisCount" to "1",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[12345_654]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS court movements IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_MOVEMENT_IN",
              "nomisCount" to "1",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[12345_789]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra unscheduled NOMIS court movements IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_MOVEMENT_IN",
              "nomisCount" to "1",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[12345_987]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class AdditionalEntitiesInDps {
      private val courtEventId: UUID = UUID.randomUUID()
      private val courtEventMovementOutId: UUID = UUID.randomUUID()
      private val courtEventMovementInId: UUID = UUID.randomUUID()
      private val courtMovementOutId: UUID = UUID.randomUUID()
      private val courtMovementInId: UUID = UUID.randomUUID()

      @BeforeEach
      fun `stub additional DPS entities and run report`() = runTest {
        dpsApi.stubGetCourtSchedulerReconciliation(
          personIdentifier = "A0001TZ",
          response = reconciliation(
            courtEvents = listOf(
              ReconciliationCourtEvent(
                courtEvent = courtEvent(id = courtEventId),
                movements = listOf(
                  courtEventMovement(id = courtEventMovementOutId),
                  courtEventMovement(id = courtEventMovementInId).copy(fromAgencyId = "LEEDMC", toAgencyId = "BXI", directionCode = "IN"),
                ),
              ),
            ),
            unscheduledMovements = listOf(
              courtEventMovement(id = courtMovementOutId),
              courtEventMovement(id = courtMovementInId).copy(fromAgencyId = "LEEDMC", toAgencyId = "BXI", directionCode = "IN"),
            ),
          ),
        )

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra DPS court event`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULE",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$courtEventId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS court movements OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_MOVEMENT_OUT",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$courtEventMovementOutId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra unscheduled DPS court movements OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_MOVEMENT_OUT",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$courtMovementOutId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS court movements IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_MOVEMENT_IN",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$courtEventMovementInId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra unscheduled DPS court movements IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_MOVEMENT_IN",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$courtMovementInId]",
            ),
          ),
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class MissingMappings {
    private val offender = "A0001TZ"
    private val courtEventId: UUID = UUID.randomUUID()
    private val courtEventMovementOutId: UUID = UUID.randomUUID()
    private val courtEventMovementInId: UUID = UUID.randomUUID()
    private val courtMovementOutId: UUID = UUID.randomUUID()
    private val courtMovementInId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() = runTest {
      reset(telemetryClient)
      nomisApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      courtScheduleNomisApi.stubGetOffenderCourtMovements(
        offenderNo = offender,
        response = offenderCourtMovementsResponse(
          courtSchedules = listOf(
            bookingCourtSchedule(
              eventId = 123,
              courtMovementOut = bookingCourtMovementOut(seq = 456),
              courtMovementIn = bookingCourtMovementIn(seq = 789),
            ),
          ),
          unscheduledCourtMovementOuts = listOf(bookingCourtMovementOut(seq = 654)),
          unscheduledCourtMovementIns = listOf(bookingCourtMovementIn(seq = 987)),
        ),
      )

      dpsApi.stubGetCourtSchedulerReconciliation(
        personIdentifier = offender,
        response = reconciliation(
          courtEvents = listOf(
            ReconciliationCourtEvent(
              courtEvent = courtEvent(id = courtEventId),
              movements = listOf(
                courtEventMovement(id = courtEventMovementOutId),
                courtEventMovement(id = courtEventMovementInId).copy(fromAgencyId = "LEEDMC", toAgencyId = "BXI", directionCode = "IN"),
              ),
            ),
          ),
          unscheduledMovements = listOf(
            courtEventMovement(id = courtMovementOutId),
            courtEventMovement(id = courtMovementInId).copy(fromAgencyId = "LEEDMC", toAgencyId = "BXI", directionCode = "IN"),
          ),
        ),
      )

      // There are no mappings
      mappingApi.stubGetCourtSchedulerPrisonerMappingIds(
        prisonerNumber = offender,
        idMappings = CourtSchedulerPrisonerMappingIdsDto(
          prisonerNumber = offender,
          schedules = listOf(),
          movements = listOf(),
        ),
      )

      reconciliationService.generateCourtSchedulerReconciliationReportBatch()
      awaitReportFinished()
    }

    @Test
    fun `should report missing court event mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to offender,
            "type" to "MISSING_MAPPING_SCHEDULE",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[123]",
            "unexpected-dps-ids" to "[$courtEventId]",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `should report missing scheduled court movement out mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to offender,
            "type" to "MISSING_MAPPING_SCHEDULED_MOVEMENT_OUT",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[12345_456]",
            "unexpected-dps-ids" to "[$courtEventMovementOutId]",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `should report missing scheduled court movement in mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to offender,
            "type" to "MISSING_MAPPING_SCHEDULED_MOVEMENT_IN",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[12345_789]",
            "unexpected-dps-ids" to "[$courtEventMovementInId]",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `should report missing unscheduled court movementout mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to offender,
            "type" to "MISSING_MAPPING_UNSCHEDULED_MOVEMENT_OUT",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[12345_654]",
            "unexpected-dps-ids" to "[$courtMovementOutId]",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `should report missing unscheduled court movement in mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to offender,
            "type" to "MISSING_MAPPING_UNSCHEDULED_MOVEMENT_IN",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[12345_987]",
            "unexpected-dps-ids" to "[$courtMovementInId]",
          ),
        ),
        isNull(),
      )
    }
  }

  @Nested
  inner class EntityDifferences {
    private val offender = "A0001TZ"
    private val courtEventId: UUID = UUID.randomUUID()
    private val courtSentencingAppearanceId: UUID = UUID.randomUUID()

    private val today = LocalDateTime.now()
    private val yesterday = today.minusDays(1)

    @Nested
    inner class CourtEvents {

      @BeforeEach
      fun setUp() = runTest {
        reset(telemetryClient)
        nomisApi.stubGetAllPrisoners(
          offenderId = 0,
          pageSize = 100,
          prisoners = listOf(generateOffenderNo(sequence = 1)),
        )
      }

      @Test
      fun `should report different prison`() = runTest {
        stubDpsCourtEvent()
        stubNomisCourtEvent(prison = "MDI")
        stubMappings()

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "123",
              "dpsCourtEventId" to "$courtEventId",
              "type" to "PRISON",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report different court`() = runTest {
        stubDpsCourtEvent()
        stubNomisCourtEvent(court = "YORKMC")
        stubMappings()

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "123",
              "dpsCourtEventId" to "$courtEventId",
              "type" to "COURT",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report different start time`() = runTest {
        stubDpsCourtEvent()
        stubNomisCourtEvent(startTime = today)
        stubMappings()

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "123",
              "dpsCourtEventId" to "$courtEventId",
              "type" to "START_TIME",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report different event type`() = runTest {
        stubDpsCourtEvent()
        stubNomisCourtEvent(eventType = "CA")
        stubMappings()

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "123",
              "dpsCourtEventId" to "$courtEventId",
              "type" to "EVENT_TYPE",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report different court sentencing URN`() = runTest {
        stubDpsCourtEvent()
        stubNomisCourtEvent(courtCaseId = 87878L)
        // The court sentencing mapping does not have a RaS UUID, so mismatch expected
        stubCourtSentencingMappings(mappings = listOf())
        stubMappings()

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "123",
              "dpsCourtEventId" to "$courtEventId",
              "type" to "EXTERNAL_REFERENCE_URN",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should NOT report different court sentencing URN`() = runTest {
        stubDpsCourtEvent()
        stubNomisCourtEvent(courtCaseId = 87878L)
        // The court sentencing UUID is mapped to the NOMIS event ID as expected, so no mismatch
        stubCourtSentencingMappings(listOf(CourtAppearanceMappingDto(123, "$courtSentencingAppearanceId")))
        stubMappings()

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(
          telemetryClient,
          never(),
        ).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "123",
              "dpsCourtEventId" to "$courtEventId",
              "type" to "EXTERNAL_REFERENCE_URN",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class ScheduledMovementsOut {
      private val dpsScheduledMovementOutId: UUID = UUID.randomUUID()

      @BeforeEach
      fun setUp() = runTest {
        reset(telemetryClient)
        nomisApi.stubGetAllPrisoners(
          offenderId = 0,
          pageSize = 100,
          prisoners = listOf(generateOffenderNo(sequence = 1)),
        )
      }

      @Test
      fun `should report different prison`() = runTest {
        stubDpsCourtEvent(movementOut = courtEventMovement(fromAgency = "MDI", id = dpsScheduledMovementOutId, directionCode = "OUT"))
        stubNomisCourtEvent(movementOut = bookingCourtMovementOut(seq = 456))
        stubMappings(NomisMovementId(12345, 456), dpsScheduledMovementOutId)

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisMovementId" to "12345_456",
              "dpsMovementId" to "$dpsScheduledMovementOutId",
              "type" to "PRISON",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report different start time`() = runTest {
        stubDpsCourtEvent(movementOut = courtEventMovement(movementTime = today, id = dpsScheduledMovementOutId, directionCode = "OUT"))
        stubNomisCourtEvent(movementOut = bookingCourtMovementOut(seq = 456))
        stubMappings(NomisMovementId(12345, 456), dpsScheduledMovementOutId)

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisMovementId" to "12345_456",
              "dpsMovementId" to "$dpsScheduledMovementOutId",
              "type" to "MOVEMENT_TIME",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report different reason`() = runTest {
        stubDpsCourtEvent(movementOut = courtEventMovement(movementReasonCode = "CA", id = dpsScheduledMovementOutId, directionCode = "OUT"))
        stubNomisCourtEvent(movementOut = bookingCourtMovementOut(seq = 456))
        stubMappings(NomisMovementId(12345, 456), dpsScheduledMovementOutId)

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisMovementId" to "12345_456",
              "dpsMovementId" to "$dpsScheduledMovementOutId",
              "type" to "REASON",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report different court`() = runTest {
        stubDpsCourtEvent(movementOut = courtEventMovement(toAgency = "YORKMC", id = dpsScheduledMovementOutId, directionCode = "OUT"))
        stubNomisCourtEvent(movementOut = bookingCourtMovementOut(seq = 456))
        stubMappings(NomisMovementId(12345, 456), dpsScheduledMovementOutId)

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisMovementId" to "12345_456",
              "dpsMovementId" to "$dpsScheduledMovementOutId",
              "type" to "COURT",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should NOT report null court`() = runTest {
        stubDpsCourtEvent(movementOut = courtEventMovement(toAgency = null, id = dpsScheduledMovementOutId, directionCode = "OUT"))
        stubNomisCourtEvent(movementOut = bookingCourtMovementOut(seq = 456, court = null))
        stubMappings(NomisMovementId(12345, 456), dpsScheduledMovementOutId)

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient, never()).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          check {
            assertThat(it["type"]).isEqualTo("COURT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class ScheduledMovementsIn {
      private val dpsScheduledMovementInId: UUID = UUID.randomUUID()

      @BeforeEach
      fun setUp() = runTest {
        reset(telemetryClient)
        nomisApi.stubGetAllPrisoners(
          offenderId = 0,
          pageSize = 100,
          prisoners = listOf(generateOffenderNo(sequence = 1)),
        )
      }

      @Test
      fun `should report different prison`() = runTest {
        stubDpsCourtEvent(movementIn = courtEventMovement(toAgency = "MDI", fromAgency = "LEEDMC", id = dpsScheduledMovementInId, directionCode = "IN"))
        stubNomisCourtEvent(movementIn = bookingCourtMovementIn(seq = 789))
        stubMappings(nomisMovementInId = NomisMovementId(12345, 789), dpsMovementInId = dpsScheduledMovementInId)

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisMovementId" to "12345_789",
              "dpsMovementId" to "$dpsScheduledMovementInId",
              "type" to "PRISON",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class UnscheduledMovementsOut {
      private val dpsUnscheduledMovementOutId: UUID = UUID.randomUUID()

      @BeforeEach
      fun setUp() = runTest {
        reset(telemetryClient)
        nomisApi.stubGetAllPrisoners(
          offenderId = 0,
          pageSize = 100,
          prisoners = listOf(generateOffenderNo(sequence = 1)),
        )
      }

      @Test
      fun `should report different prison`() = runTest {
        stubDpsUnscheduledCourtMovement(courtEventMovement(fromAgency = "MDI", id = dpsUnscheduledMovementOutId, directionCode = "OUT"))
        stubNomisUnscheduledCourtMovementOut(bookingCourtMovementOut(seq = 654))
        stubMappings(nomisMovementOutId = NomisMovementId(12345, 654), dpsMovementOutId = dpsUnscheduledMovementOutId)

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisMovementId" to "12345_654",
              "dpsMovementId" to "$dpsUnscheduledMovementOutId",
              "type" to "PRISON",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class UnscheduledMovementsIn {
      private val dpsUnscheduledMovementInId: UUID = UUID.randomUUID()

      @BeforeEach
      fun setUp() = runTest {
        reset(telemetryClient)
        nomisApi.stubGetAllPrisoners(
          offenderId = 0,
          pageSize = 100,
          prisoners = listOf(generateOffenderNo(sequence = 1)),
        )
      }

      @Test
      fun `should report different court`() = runTest {
        stubDpsUnscheduledCourtMovement(courtEventMovement(toAgency = "BXI", fromAgency = "YORKMC", id = dpsUnscheduledMovementInId, directionCode = "IN"))
        stubNomisUnscheduledCourtMovementIn(bookingCourtMovementIn(seq = 987))
        stubMappings(nomisMovementInId = NomisMovementId(12345, 987), dpsMovementInId = dpsUnscheduledMovementInId)

        reconciliationService.generateCourtSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisMovementId" to "12345_987",
              "dpsMovementId" to "$dpsUnscheduledMovementInId",
              "type" to "COURT",
            ),
          ),
          isNull(),
        )
      }
    }

    private fun stubNomisCourtEvent(
      prison: String = "BXI",
      court: String = "LEEDMC",
      startTime: LocalDateTime = yesterday,
      eventType: String = "CRT",
      eventStatus: String = "COMP",
      movementOut: BookingCourtMovementOut? = null,
      movementIn: BookingCourtMovementIn? = null,
      courtCaseId: Long? = null,
    ) = courtScheduleNomisApi.stubGetOffenderCourtMovements(
      offenderNo = offender,
      response = offenderCourtMovementsResponse(
        courtSchedules = listOf(
          BookingCourtScheduleOut(
            eventId = 123,
            eventDate = startTime.toLocalDate(),
            startTime = startTime,
            eventType = eventType,
            eventStatus = eventStatus,
            prison = prison,
            court = court,
            audit = NomisAudit(
              createDatetime = yesterday,
              createUsername = "USER",
            ),
            courtMovementOut = movementOut,
            courtMovementIn = movementIn,
            comment = "Some schedule comment",
            courtCaseId = courtCaseId,
          ),
        ),
        unscheduledCourtMovementOuts = listOf(),
        unscheduledCourtMovementIns = listOf(),
      ),
    )

    private fun stubNomisUnscheduledCourtMovementOut(
      movementOut: BookingCourtMovementOut,
    ) = courtScheduleNomisApi.stubGetOffenderCourtMovements(
      offenderNo = offender,
      response = offenderCourtMovementsResponse(
        courtSchedules = listOf(),
        unscheduledCourtMovementOuts = listOf(movementOut),
        unscheduledCourtMovementIns = listOf(),
      ),
    )

    private fun stubNomisUnscheduledCourtMovementIn(
      movementIn: BookingCourtMovementIn,
    ) = courtScheduleNomisApi.stubGetOffenderCourtMovements(
      offenderNo = offender,
      response = offenderCourtMovementsResponse(
        courtSchedules = listOf(),
        unscheduledCourtMovementOuts = listOf(),
        unscheduledCourtMovementIns = listOf(movementIn),
      ),
    )

    private fun stubDpsCourtEvent(
      movementOut: CourtEventMovement? = null,
      movementIn: CourtEventMovement? = null,
    ) = dpsApi.stubGetCourtSchedulerReconciliation(
      personIdentifier = offender,
      response = reconciliation(
        courtEvents = listOf(
          ReconciliationCourtEvent(
            courtEvent = CourtEvent(
              dpsId = courtEventId,
              prisonCodeAtTimeOfScheduling = "BXI",
              agyLocId = "LEEDMC",
              start = yesterday,
              courtEventType = "CRT",
              eventStatus = "COMP",
              commentText = "Some schedule comment",
              externalReferenceUrn = EXTERNAL_REF_PREFIX + courtSentencingAppearanceId,
            ),
            movements = listOfNotNull(movementOut, movementIn),
          ),
        ),
        unscheduledMovements = listOf(),
      ),
    )

    private fun stubDpsUnscheduledCourtMovement(
      movement: CourtEventMovement,
    ) = dpsApi.stubGetCourtSchedulerReconciliation(
      personIdentifier = offender,
      response = reconciliation(
        courtEvents = listOf(),
        unscheduledMovements = listOf(movement),
      ),
    )

    private fun stubMappings(
      nomisMovementOutId: NomisMovementId? = null,
      dpsMovementOutId: UUID? = null,
      nomisMovementInId: NomisMovementId? = null,
      dpsMovementInId: UUID? = null,
    ) = mappingApi.stubGetCourtSchedulerPrisonerMappingIds(
      prisonerNumber = offender,
      idMappings = CourtSchedulerPrisonerMappingIdsDto(
        prisonerNumber = offender,
        schedules = listOf(CourtScheduleMappingIdsDto(123, courtEventId)),
        movements = listOfNotNull(
          nomisMovementOutId?.let { CourtMovementMappingIdsDto(nomisMovementOutId.bookingId, nomisMovementOutId.sequence, dpsMovementOutId!!) },
          nomisMovementInId?.let { CourtMovementMappingIdsDto(nomisMovementInId.bookingId, nomisMovementInId.sequence, dpsMovementInId!!) },
        ),
      ),
    )

    private fun stubCourtSentencingMappings(
      mappings: List<CourtAppearanceMappingDto> = listOf(),
    ) = courtSentencingMappingApi.stubGetAllCourtAppearanceByNomisIds(
      mappings = mappings,
    )
  }

  @Nested
  inner class PrisonerReconciliationEndpoint {

    @Nested
    inner class ReconcileSinglePrisoner {

      @BeforeEach
      fun setUp() = runTest {
        stubEmptyResponses("A0001TZ")
      }

      @Test
      fun `should return nothing if no mismatches`() = runTest {
        webTestClient.get().uri("/external-movements/court/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$.size()").isEqualTo(0)

        verify(
          telemetryClient,
          never(),
        ).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `should return ID mismatches and suppress telemetry`() = runTest {
        courtScheduleNomisApi.stubGetOffenderCourtMovements(
          offenderNo = "A0001TZ",
          response = offenderCourtMovementsResponse(
            courtSchedules = listOf(
              bookingCourtSchedule(
                eventId = 123,
                courtMovementOut = null,
                courtMovementIn = null,
              ),
            ),
            unscheduledCourtMovementOuts = listOf(),
            unscheduledCourtMovementIns = listOf(),
          ),
        )

        // returns full mismatch details
        webTestClient.get().uri("/external-movements/court/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$[0].offenderNo").isEqualTo("A0001TZ")
          .jsonPath("$[0].type").isEqualTo("SCHEDULE")
          .jsonPath("$[0].nomisCount").isEqualTo("1")
          .jsonPath("$[0].dpsCount").isEqualTo("0")
          .jsonPath("$[0].unexpectedNomisIds").isEqualTo("[123]")
          .jsonPath("$[0].unexpectedDpsIds").isEqualTo("[]")

        // does not publish telemetry
        verify(
          telemetryClient,
          never(),
        ).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `should return detail mismatches and suppress telemetry`() = runTest {
        val dpsCourtEventId = UUID.randomUUID()
        val today = LocalDateTime.now()

        courtScheduleNomisApi.stubGetOffenderCourtMovements(
          offenderNo = "A0001TZ",
          response = offenderCourtMovementsResponse(
            courtSchedules = listOf(
              bookingCourtSchedule(
                eventId = 123,
                startTime = today,
                // The prison is different to DPS
                prison = "MDI",
                courtMovementOut = null,
                courtMovementIn = null,
              ),
            ),
            unscheduledCourtMovementOuts = listOf(),
            unscheduledCourtMovementIns = listOf(),
          ),
        )
        dpsApi.stubGetCourtSchedulerReconciliation(
          personIdentifier = "A0001TZ",
          response = reconciliation(
            courtEvents = listOf(
              ReconciliationCourtEvent(
                courtEvent = courtEvent(
                  id = dpsCourtEventId,
                  startTime = today,
                  // The prison is different to NOMIS
                  prison = "BXI",
                ),
                movements = listOf(),
              ),
            ),
            unscheduledMovements = listOf(),
          ),
        )
        mappingApi.stubGetCourtSchedulerPrisonerMappingIds(
          prisonerNumber = "A0001TZ",
          idMappings = CourtSchedulerPrisonerMappingIdsDto(
            prisonerNumber = "A0001TZ",
            schedules = listOf(CourtScheduleMappingIdsDto(123, dpsCourtEventId)),
            movements = listOf(),
          ),
        )

        // returns full mismatch details
        webTestClient.get().uri("/external-movements/court/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$[0].offenderNo").isEqualTo("A0001TZ")
          .jsonPath("$[0].type").isEqualTo("PRISON")
          .jsonPath("$[0].nomisEventId").isEqualTo("123")
          .jsonPath("$[0].dpsCourtEventId").isEqualTo("$dpsCourtEventId")
          .jsonPath("$[0].nomisValue").isEqualTo("MDI")
          .jsonPath("$[0].dpsValue").isEqualTo("BXI")

        // does not publish telemetry
        verify(
          telemetryClient,
          never(),
        ).trackEvent(
          eq("court-scheduler-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return error for unknown offender`() {
        webTestClient.get().uri("/external-movements/court/UNKNOWN/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().is5xxServerError
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/external-movements/court/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/external-movements/court/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/external-movements/court/A0001TZ/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }
  }

  private fun stubEmptyResponses(offender: String = "A0001TZ") {
    courtScheduleNomisApi.stubGetOffenderCourtMovements(
      offenderNo = offender,
      response = offenderCourtMovementsResponse(
        courtSchedules = listOf(),
        unscheduledCourtMovementOuts = listOf(),
        unscheduledCourtMovementIns = listOf(),
      ),
    )

    dpsApi.stubGetCourtSchedulerReconciliation(
      personIdentifier = offender,
      response = reconciliation(
        courtEvents = listOf(),
        unscheduledMovements = listOf(),
      ),
    )

    mappingApi.stubGetCourtSchedulerPrisonerMappingIds(
      prisonerNumber = offender,
      idMappings = CourtSchedulerPrisonerMappingIdsDto(
        prisonerNumber = offender,
        schedules = listOf(),
        movements = listOf(),
      ),
    )
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("court-scheduler-reconciliation-report"), any(), isNull()) }
  }
}
