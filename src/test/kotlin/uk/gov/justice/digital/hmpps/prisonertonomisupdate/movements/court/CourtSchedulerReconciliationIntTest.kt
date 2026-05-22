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
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.ReconciliationCourtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiExtension.Companion.courtSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiMockServer.Companion.courtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiMockServer.Companion.courtEventMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiMockServer.Companion.reconciliation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtMovementIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtSchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.offenderCourtMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.util.UUID

class CourtSchedulerReconciliationIntTest(
  @Autowired private val reconciliationService: CourtSchedulerReconciliationService,
  @Autowired private val courtScheduleNomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
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
        eq("temporary-absences-active-reconciliation-requested"),
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
          assertThat(it).containsEntry("mismatch-count", "0")
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
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("court-scheduler-reconciliation-report"), any(), isNull()) }
  }
}
