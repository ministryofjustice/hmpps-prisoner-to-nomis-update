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
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiExtension.Companion.transferSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiMockServer.Companion.reconciliation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiMockServer.Companion.transferMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiMockServer.Companion.transferSchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerNomisApiMockServer.Companion.offenderTransferMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerNomisApiMockServer.Companion.transferMovementOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerNomisApiMockServer.Companion.transferScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTransferSchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.ReconciliationTransfer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncTransfer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.util.*

class TransferSchedulerReconciliationIntTest(
  @Autowired private val reconciliationService: TransferScheduleReconciliationService,
  @Autowired private val nomisApi: TransferSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: TransferSchedulerMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = transferSchedulerDpsApiServer
  private val nomisPrisonerApi = NomisApiExtension.nomisApi

  @DisplayName("Generate reconciliation report")
  @Nested
  inner class GenerateReconciliationReportBatch {
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
      reconciliationService.generateTransferSchedulerReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateTransferSchedulerReconciliationReportBatch()
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

      reconciliationService.generateTransferSchedulerReconciliationReportBatch()
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

        reconciliationService.generateTransferSchedulerReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS transfer schedule`() {
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
      fun `should report extra NOMIS scheduled movement`() {
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
      fun `should report extra NOMIS unscheduled movement`() {
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

        reconciliationService.generateTransferSchedulerReconciliationReportBatch()
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

      reconciliationService.generateTransferSchedulerReconciliationReportBatch()
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

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("transfer-scheduler-reconciliation-report"), any(), isNull()) }
  }
}
