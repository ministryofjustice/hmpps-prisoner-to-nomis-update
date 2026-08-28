package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiExtension.Companion.transferSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiMockServer.Companion.reconciliation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiMockServer.Companion.transferMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiMockServer.Companion.transferSchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiMockServer.Companion.transferWaitlist
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerNomisApiMockServer.Companion.offenderTransferMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerNomisApiMockServer.Companion.transferMovementOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerNomisApiMockServer.Companion.transferScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerNomisApiMockServer.Companion.transferScheduleWaitlistResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferScheduleMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTransferSchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TransferMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TransferScheduleWaitlist
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.ReconciliationTransfer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncTransfer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncWaitlist
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class TransferSchedulerReconciliationIntTest(
  @Autowired private val allPrisonersReconciliationService: TransferSchedulerReconciliationServiceAllPrisoners,
  @Autowired private val activePrisonersReconciliationService: TransferSchedulerReconciliationServiceActivePrisoners,
  @Autowired private val nomisApi: TransferSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: TransferSchedulerMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = transferSchedulerDpsApiServer
  private val nomisPrisonerApi = NomisApiExtension.nomisApi

  @DisplayName("Generate reconciliation report - all prisoners")
  @Nested
  inner class GenerateReconciliationReportBatchAllPrisoners {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisPrisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      stubEmptyResponses(offender = "A0001TZ")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-requested"),
        check {
          assertThat(it["TYPE"]).isEqualTo("ALL")
        },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-report"),
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
      nomisApi.stubGetOffenderTransferMovements(status = INTERNAL_SERVER_ERROR)

      allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch-error"),
        check {
          assertThat(it).containsEntry("offenderNo", "A0001TZ")
          assertThat(it).containsEntry("reason", "500 Internal Server Error from GET http://localhost:8082/movements/A0001TZ/transfer")
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
      nomisPrisonerApi.stubGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(
          lastBookingId = 12345,
          prisonerIds = listOf(PrisonerIds(12345L, generateOffenderNo(sequence = 1))),
        ),
      )

      stubEmptyResponses(offender = "A0001TZ")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      activePrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-requested"),
        check {
          assertThat(it["TYPE"]).isEqualTo("ACTIVE")
        },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      activePrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-report"),
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
      nomisApi.stubGetOffenderTransferMovements(status = INTERNAL_SERVER_ERROR)

      activePrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch-error"),
        check {
          assertThat(it).containsEntry("offenderNo", "A0001TZ")
          assertThat(it).containsEntry("reason", "500 Internal Server Error from GET http://localhost:8082/movements/A0001TZ/transfer")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class AdditionalEntities {
    private val offender = "A0001TZ"

    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisPrisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      stubEmptyResponses(offender)
    }

    @Nested
    inner class AdditionalEntitiesInNomis {

      @BeforeEach
      fun `stub additional NOMIS entities and run report`() = runTest {
        nomisApi.stubGetOffenderTransferMovements(
          offenderNo = offender,
          response = offenderTransferMovementsResponse(
            offenderNo = offender,
            schedules = listOf(
              BookingTransferSchedule(
                schedule = transferScheduleOutResponse(eventId = 123L),
                movement = transferMovementOutResponse().copy(eventId = 123L, sequence = 3),
              ),
            ),
            unscheduledMovements = listOf(transferMovementOutResponse().copy(transferScheduleOutId = null, sequence = 4)),
          ),
        )
      }

      @Test
      fun `should report extra NOMIS transfer schedule`() = runTest {
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to offender,
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
      fun `should report extra NOMIS scheduled movement`() = runTest {
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to offender,
              "type" to "SCHEDULED_MOVEMENT",
              "nomisCount" to "1",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[12345_3]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS unscheduled movement`() = runTest {
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to offender,
              "type" to "UNSCHEDULED_MOVEMENT",
              "nomisCount" to "1",
              "dpsCount" to "0",
              "unexpected-nomis-ids" to "[12345_4]",
              "unexpected-dps-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `reconcile endpoint should return ID mismatches`() = runTest {
        webTestClient.get().uri("/external-movements/transfer/$offender/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$.length()").isEqualTo(6)
          .jsonPath("$[0].offenderNo").isEqualTo(offender)
          .jsonPath("$[0].type").isEqualTo("SCHEDULE")
          .jsonPath("$[0].nomisCount").isEqualTo("1")
          .jsonPath("$[0].dpsCount").isEqualTo("0")
          .jsonPath("$[0].unexpectedNomisIds").isEqualTo("[123]")
          .jsonPath("$[0].unexpectedDpsIds").isEqualTo("[]")

        // No telemetry
        verify(telemetryClient, never()).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class AdditionalEntitiesInDps {
      private val dpsScheduleId = UUID.randomUUID()
      private val dpsScheduledMovementId = UUID.randomUUID()
      private val dpsUnscheduledMovementId = UUID.randomUUID()

      @BeforeEach
      fun `stub additional DPS entities and run report`() = runTest {
        dpsApi.stubGetTransferSchedulerReconciliation(
          personIdentifier = offender,
          response = reconciliation(
            transfers = listOf(
              ReconciliationTransfer(
                transfer = SyncTransfer(dpsId = dpsScheduleId, schedule = transferSchedule()),
                movement = transferMovement(dpsId = dpsScheduledMovementId),
              ),
            ),
            unscheduledMovements = listOf(transferMovement(dpsId = dpsUnscheduledMovementId)),
          ),
        )

        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra DPS transfer schedule`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to offender,
              "type" to "SCHEDULE",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$dpsScheduleId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS scheduled movement`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to offender,
              "type" to "SCHEDULED_MOVEMENT",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$dpsScheduledMovementId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS unscheduled movement`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to offender,
              "type" to "UNSCHEDULED_MOVEMENT",
              "nomisCount" to "0",
              "dpsCount" to "1",
              "unexpected-nomis-ids" to "[]",
              "unexpected-dps-ids" to "[$dpsUnscheduledMovementId]",
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
    private val dpsScheduleId = UUID.randomUUID()
    private val dpsScheduledMovementId = UUID.randomUUID()
    private val dpsUnscheduledMovementId = UUID.randomUUID()

    @BeforeEach
    fun setUp() = runTest {
      reset(telemetryClient)
      nomisPrisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      // stub NOMIS transfers
      nomisApi.stubGetOffenderTransferMovements(
        offenderNo = offender,
        response = offenderTransferMovementsResponse(
          offenderNo = offender,
          schedules = listOf(
            BookingTransferSchedule(
              schedule = transferScheduleOutResponse(eventId = 123L),
              movement = transferMovementOutResponse().copy(eventId = 123L, sequence = 3),
            ),
          ),
          unscheduledMovements = listOf(transferMovementOutResponse().copy(transferScheduleOutId = null, sequence = 4)),
        ),
      )

      // stub DPS transfers
      dpsApi.stubGetTransferSchedulerReconciliation(
        personIdentifier = offender,
        response = reconciliation(
          transfers = listOf(
            ReconciliationTransfer(
              transfer = SyncTransfer(dpsId = dpsScheduleId, schedule = transferSchedule()),
              movement = transferMovement(dpsId = dpsScheduledMovementId),
            ),
          ),
          unscheduledMovements = listOf(transferMovement(dpsId = dpsUnscheduledMovementId)),
        ),
      )

      // stub no mappings
      mappingApi.stubGetTransferSchedulerPrisonerMappingIds(
        prisonerNumber = offender,
        idMappings = TransferSchedulerPrisonerMappingIdsDto(
          prisonerNumber = offender,
          schedules = listOf(),
          movements = listOf(),
        ),
      )

      allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
      awaitReportFinished()
    }

    @Test
    fun `should report missing schedule mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to offender,
            "type" to "MISSING_MAPPING_SCHEDULE",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[123]",
            "unexpected-dps-ids" to "[$dpsScheduleId]",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `should report missing scheduled movement mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to offender,
            "type" to "MISSING_MAPPING_SCHEDULED_MOVEMENT",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[12345_3]",
            "unexpected-dps-ids" to "[$dpsScheduledMovementId]",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `should report missing unscheduled movement mappings`() = runTest {
      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to offender,
            "type" to "MISSING_MAPPING_UNSCHEDULED_MOVEMENT",
            "nomisCount" to "1",
            "dpsCount" to "1",
            "unexpected-nomis-ids" to "[12345_4]",
            "unexpected-dps-ids" to "[$dpsUnscheduledMovementId]",
          ),
        ),
        isNull(),
      )
    }
  }

  @Nested
  inner class PropertyDifferences {
    private val offender = "A0001TZ"
    private val dpsScheduleId = UUID.randomUUID()
    private val dpsScheduledMovementId = UUID.randomUUID()
    private val dpsUnscheduledMovementId = UUID.randomUUID()
    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)

    @BeforeEach
    fun setUp() = runTest {
      reset(telemetryClient)
      nomisPrisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )
    }

    @Nested
    inner class Schedules {

      private fun verifyTelemetry(type: String) = verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0001TZ",
            "nomisEventId" to "123",
            "dpsScheduleId" to "$dpsScheduleId",
            "type" to type,
          ),
        ),
        isNull(),
      )

      @Test
      fun `should not report if there are no differences`() = runTest {
        stubNomis()
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verify(telemetryClient, never()).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `should NOT report different event status`() = runTest {
        stubNomis(schedule = stubNomisSchedule(eventStatus = "CANC"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verify(telemetryClient, never()).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `should report different start time`() = runTest {
        stubNomis(schedule = stubNomisSchedule(startTime = yesterday))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("SCHEDULE_START_TIME")
      }

      @Test
      fun `should report different event subtype`() = runTest {
        stubNomis(schedule = stubNomisSchedule(eventSubType = "29"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("SCHEDULE_EVENT_SUBTYPE")
      }

      @Test
      fun `should report different from prison`() = runTest {
        stubNomis(schedule = stubNomisSchedule(fromPrison = "MDI"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("SCHEDULE_FROM_PRISON")
      }

      @Test
      fun `should report different to prison`() = runTest {
        stubNomis(schedule = stubNomisSchedule(toPrison = "MDI"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("SCHEDULE_TO_PRISON")
      }

      @Test
      fun `should report different comment`() = runTest {
        stubNomis(schedule = stubNomisSchedule(comment = "different"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("SCHEDULE_COMMENT")
      }

      @Test
      fun `should report different hidden comment`() = runTest {
        stubNomis(schedule = stubNomisSchedule(hiddenComment = "different"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("SCHEDULE_HIDDEN_COMMENT")
      }

      @Test
      fun `should report different outcome`() = runTest {
        stubNomis(schedule = stubNomisSchedule(cancellationReasonCode = "TRANS"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("SCHEDULE_CANCELLATION_REASON")
      }

      @Test
      fun `should report different escort code`() = runTest {
        stubNomis(schedule = stubNomisSchedule(escortCode = "GEO"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("SCHEDULE_ESCORT")
      }

      @Test
      fun `reconcile endpoint should schedule detail mismatches`() = runTest {
        stubNomis(schedule = stubNomisSchedule(startTime = yesterday))
        stubDps()
        stubMappings()

        webTestClient.get().uri("/external-movements/transfer/$offender/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$.length()").isEqualTo(1)
          .jsonPath("$[0].offenderNo").isEqualTo(offender)
          .jsonPath("$[0].type").isEqualTo("SCHEDULE_START_TIME")
          .jsonPath("$[0].nomisValue").isEqualTo("$yesterday")
          .jsonPath("$[0].dpsValue").isEqualTo("$now")

        // No telemetry
        verify(telemetryClient, never()).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class Waitlists {

      private fun verifyTelemetry(type: String) = verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0001TZ",
            "nomisEventId" to "123",
            "dpsScheduleId" to "$dpsScheduleId",
            "type" to type,
          ),
        ),
        isNull(),
      )

      @Test
      fun `should report waitlist missing`() = runTest {
        stubNomis(waitlist = null)
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("WAITLIST")
      }

      @Test
      fun `should report different start time`() = runTest {
        stubNomis(waitlist = stubNomisWaitlist(requestDate = now.toLocalDate()))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("WAITLIST_REQUESTED_DATE")
      }

      @Test
      fun `should report different status date`() = runTest {
        stubNomis(waitlist = stubNomisWaitlist(statusDate = yesterday.toLocalDate()))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("WAITLIST_STATUS_DATE")
      }

      @Test
      fun `should report different transfer priority`() = runTest {
        stubNomis(waitlist = stubNomisWaitlist(priority = "1"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("WAITLIST_TRANSFER_PRIORITY")
      }

      @Test
      fun `should report different approved flag`() = runTest {
        stubNomis(waitlist = stubNomisWaitlist(approved = false))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("WAITLIST_APPROVED")
      }

      @Test
      fun `should report different cancellation reason`() = runTest {
        stubNomis(waitlist = stubNomisWaitlist(cancellationReasonCode = "TRANS"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("WAITLIST_CANCELLATION_REASON")
      }

      @Test
      fun `should report different comment`() = runTest {
        stubNomis(waitlist = stubNomisWaitlist(comment = "different"))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("WAITLIST_COMMENT")
      }
    }

    @Nested
    inner class ScheduledMovements {

      private fun verifyTelemetry(type: String) = verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0001TZ",
            "nomisMovementId" to "12345_3",
            "dpsMovementId" to "$dpsScheduledMovementId",
            "type" to type,
          ),
        ),
        isNull(),
      )

      @Test
      fun `should report movement time difference`() = runTest {
        stubNomis(scheduledMovement = stubNomisMovement(movementTime = yesterday, eventId = 123, sequence = 3))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_TIME")
      }

      @Test
      fun `should report movement reason difference`() = runTest {
        stubNomis(scheduledMovement = stubNomisMovement(movementReason = "29", eventId = 123, sequence = 3))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_REASON")
      }

      @Test
      fun `should report from prison difference`() = runTest {
        stubNomis(scheduledMovement = stubNomisMovement(fromPrison = "MDI", eventId = 123, sequence = 3))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_FROM_PRISON")
      }

      @Test
      fun `should report to prison difference`() = runTest {
        stubNomis(scheduledMovement = stubNomisMovement(toPrison = "MDI", eventId = 123, sequence = 3))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_TO_PRISON")
      }

      @Test
      fun `should report active flag difference`() = runTest {
        stubNomis(scheduledMovement = stubNomisMovement(active = false, eventId = 123, sequence = 3))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_ACTIVE")
      }

      @Test
      fun `should report comment difference`() = runTest {
        stubNomis(scheduledMovement = stubNomisMovement(commentText = "different", eventId = 123, sequence = 3))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_COMMENT")
      }
    }

    @Nested
    inner class UnscheduledMovements {

      private fun verifyTelemetry(type: String) = verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0001TZ",
            "nomisMovementId" to "12345_4",
            "dpsMovementId" to "$dpsUnscheduledMovementId",
            "type" to type,
          ),
        ),
        isNull(),
      )

      @Test
      fun `should report movement time difference`() = runTest {
        stubNomis(unscheduledMovement = stubNomisMovement(movementTime = yesterday, eventId = null, sequence = 4))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_TIME")
      }

      @Test
      fun `should report movement reason difference`() = runTest {
        stubNomis(unscheduledMovement = stubNomisMovement(movementReason = "29", eventId = null, sequence = 4))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_REASON")
      }

      @Test
      fun `should report from prison difference`() = runTest {
        stubNomis(unscheduledMovement = stubNomisMovement(fromPrison = "MDI", eventId = null, sequence = 4))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_FROM_PRISON")
      }

      @Test
      fun `should report to prison difference`() = runTest {
        stubNomis(unscheduledMovement = stubNomisMovement(toPrison = "MDI", eventId = null, sequence = 4))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_TO_PRISON")
      }

      @Test
      fun `should report active flag difference`() = runTest {
        stubNomis(unscheduledMovement = stubNomisMovement(active = false, eventId = null, sequence = 4))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_ACTIVE")
      }

      @Test
      fun `should report comment difference`() = runTest {
        stubNomis(unscheduledMovement = stubNomisMovement(commentText = "different", eventId = null, sequence = 4))
        stubDps()
        stubMappings()
        allPrisonersReconciliationService.generateTransferSchedulerReconciliationReportBatch()
          .also { awaitReportFinished() }

        verifyTelemetry("MOVEMENT_COMMENT")
      }
    }

    fun stubNomis(
      schedule: TransferScheduleOut = stubNomisSchedule(),
      waitlist: TransferScheduleWaitlist? = stubNomisWaitlist(),
      scheduledMovement: TransferMovementOut = stubNomisMovement(eventId = schedule.eventId, sequence = 3),
      unscheduledMovement: TransferMovementOut = stubNomisMovement(eventId = null, sequence = 4),
    ) {
      nomisApi.stubGetOffenderTransferMovements(
        offenderNo = offender,
        response = offenderTransferMovementsResponse(
          offenderNo = offender,
          schedules = listOf(
            BookingTransferSchedule(
              schedule = schedule.copy(waitlist = waitlist),
              movement = scheduledMovement,
            ),
          ),
          unscheduledMovements = listOf(unscheduledMovement),
        ),
      )
    }

    fun stubNomisSchedule(
      eventId: Long = 123L,
      eventStatus: String = "SCH",
      startTime: LocalDateTime = now,
      eventSubType: String = "TRN",
      fromPrison: String = "BXI",
      toPrison: String = "LEI",
      comment: String = "some schedule comment",
      hiddenComment: String = "some hidden comment",
      cancellationReasonCode: String = "ADMI",
      escortCode: String = "PECS",
      waitlist: TransferScheduleWaitlist? = null,
    ) = transferScheduleOutResponse().copy(
      eventId = eventId,
      eventStatus = eventStatus,
      startTime = startTime,
      eventSubType = eventSubType,
      fromPrison = fromPrison,
      toPrison = toPrison,
      comment = comment,
      hiddenComment = hiddenComment,
      cancellationReasonCode = cancellationReasonCode,
      escortCode = escortCode,
      waitlist = waitlist,
    )

    fun stubNomisWaitlist(
      requestDate: LocalDate = yesterday.toLocalDate(),
      status: String = "PEND",
      statusDate: LocalDate = now.toLocalDate(),
      priority: String = "3",
      approved: Boolean = true,
      cancellationReasonCode: String = "ADMI",
      comment: String = "some waitlist comment",
      approvedUserName: String = "some user",
    ) = transferScheduleWaitlistResponse().copy(
      requestDate = requestDate,
      status = status,
      statusDate = statusDate,
      priority = priority,
      approved = approved,
      cancellationReasonCode = cancellationReasonCode,
      comment = comment,
      approvedUserName = approvedUserName,
    )

    fun stubNomisMovement(
      eventId: Long? = 123L,
      sequence: Int = 3,
      movementTime: LocalDateTime = now,
      movementReason: String = "28",
      fromPrison: String = "BXI",
      toPrison: String = "LEI",
      active: Boolean = true,
      transferScheduleOutId: Long? = null,
      commentText: String? = "some transfer movement comment",
    ) = transferMovementOutResponse().copy(
      eventId = eventId,
      sequence = sequence,
      movementTime = movementTime,
      movementReason = movementReason,
      fromPrison = fromPrison,
      toPrison = toPrison,
      active = active,
      transferScheduleOutId = transferScheduleOutId,
      commentText = commentText,
    )

    fun stubDps() {
      dpsApi.stubGetTransferSchedulerReconciliation(
        personIdentifier = offender,
        response = reconciliation(
          listOf(
            ReconciliationTransfer(
              transfer = SyncTransfer(dpsId = dpsScheduleId, schedule = stubDpsSchedule(), waitlist = stubDpsWaitlist()),
              movement = stubDpsMovement(dpsId = dpsScheduledMovementId, dpsTransferId = dpsScheduleId),
            ),
          ),
          listOf(
            stubDpsMovement(dpsId = dpsUnscheduledMovementId, dpsTransferId = null),
          ),
        ),
      )
    }

    fun stubDpsWaitlist(
      requestDate: LocalDate = yesterday.toLocalDate(),
      waitListStatus: String = "PEND",
      statusDate: LocalDate = now.toLocalDate(),
      transferPriority: String = "3",
      approved: Boolean = true,
      approvedUsername: String = "some user",
      outcomeReasonCode: SyncWaitlist.OutcomeReasonCode = SyncWaitlist.OutcomeReasonCode.ADMI,
      commentText1: String = "some waitlist comment",
    ) = transferWaitlist().copy(
      requestDate = requestDate,
      waitListStatus = waitListStatus,
      statusDate = statusDate,
      transferPriority = transferPriority,
      approved = approved,
      approvedUsername = approvedUsername,
      outcomeReasonCode = outcomeReasonCode,
      commentText1 = commentText1,
    )

    fun stubDpsSchedule(
      start: LocalDateTime = now,
      eventSubType: String = "TRN",
      eventStatus: String = "SCH",
      commentText: String = "some schedule comment",
      hiddenCommentText: String = "some hidden comment",
      agyLocId: String = "BXI",
      toAgyLocId: String = "LEI",
      outcomeReasonCode: String = "ADMI",
      escortCode: String = "PECS",
    ) = transferSchedule().copy(
      start = start,
      eventSubType = eventSubType,
      eventStatus = eventStatus,
      commentText = commentText,
      hiddenCommentText = hiddenCommentText,
      agyLocId = agyLocId,
      toAgyLocId = toAgyLocId,
      outcomeReasonCode = outcomeReasonCode,
      escortCode = escortCode,
    )

    fun stubDpsMovement(
      dpsId: UUID,
      dpsTransferId: UUID?,
      offenderBookId: Long = 12345L,
      movementSeq: Int = 3,
      occurredAt: LocalDateTime = now,
      movementReasonCode: String = "28",
      escortCode: String = "PECS",
      fromAgyLocId: String = "BXI",
      toAgyLocId: String = "LEI",
      active: Boolean = true,
      commentText: String = "some transfer movement comment",
    ) = transferMovement(dpsId, dpsTransferId).copy(
      dpsId = dpsId,
      dpsTransferId = dpsTransferId,
      offenderBookId = offenderBookId,
      movementSeq = movementSeq,
      occurredAt = occurredAt,
      movementReasonCode = movementReasonCode,
      escortCode = escortCode,
      fromAgyLocId = fromAgyLocId,
      toAgyLocId = toAgyLocId,
      active = active,
      commentText = commentText,
    )

    fun stubMappings() = mappingApi.stubGetTransferSchedulerPrisonerMappingIds(
      prisonerNumber = offender,
      idMappings = TransferSchedulerPrisonerMappingIdsDto(
        prisonerNumber = offender,
        schedules = listOf(TransferScheduleMappingIdsDto(123L, dpsScheduleId)),
        movements = listOf(
          TransferMovementMappingIdsDto(12345L, 3, dpsScheduledMovementId),
          TransferMovementMappingIdsDto(12345L, 4, dpsUnscheduledMovementId),
        ),
      ),
    )
  }

  private fun stubEmptyResponses(offender: String = "A0001TZ") {
    nomisApi.stubGetOffenderTransferMovements(
      offenderNo = offender,
      response = offenderTransferMovementsResponse(
        schedules = listOf(),
        unscheduledMovements = listOf(),
      ),
    )

    dpsApi.stubGetTransferSchedulerReconciliation(
      personIdentifier = offender,
      response = reconciliation(listOf(), listOf()),
    )

    mappingApi.stubGetTransferSchedulerPrisonerMappingIds(
      prisonerNumber = offender,
      idMappings = TransferSchedulerPrisonerMappingIdsDto(
        prisonerNumber = offender,
        schedules = listOf(),
        movements = listOf(),
      ),
    )
  }

  @Nested
  inner class PrisonerReconciliationEndpoint {

    @Nested
    // Note that there are various tests in this class which test the reconciliation endpoint works
    // the same as the batch job
    inner class ReconcileSinglePrisoner {

      @BeforeEach
      fun setUp() = runTest {
        stubEmptyResponses("A0001TZ")
      }

      @Test
      fun `should return nothing if no mismatches`() = runTest {
        webTestClient.get().uri("/external-movements/transfer/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk()
          .expectBody()
          .jsonPath("$.mismatches").doesNotExist()

        // No telemetry
        verify(telemetryClient, never()).trackEvent(
          eq("transfer-scheduler-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return error for unknown offender`() {
        webTestClient.get().uri("/external-movements/transfer/UNKNOWN/reconciliation")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().is5xxServerError
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/external-movements/transfer/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/external-movements/transfer/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/external-movements/transfer/A0001TZ/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("transfer-scheduler-reconciliation-report"), any(), isNull()) }
  }
}
