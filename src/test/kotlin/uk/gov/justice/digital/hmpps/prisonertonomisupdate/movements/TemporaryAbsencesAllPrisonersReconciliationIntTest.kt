package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

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
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.TapMismatchTypes.AUTHORISATIONS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.MovementInOutCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonAuthorisationCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonMovementsCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonOccurrenceCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapCounts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Applications
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Movements
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementsByDirection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ScheduledOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.util.UUID
import kotlin.random.Random

class TemporaryAbsencesAllPrisonersReconciliationIntTest(
  @Autowired private val reconciliationService: TemporaryAbsencesAllPrisonersReconciliationService,
  @Autowired private val nomisApi: ExternalMovementsNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = ExternalMovementsDpsApiExtension.dpsExternalMovementsServer

  @DisplayName("Temporary absences all prisoners reconciliation report")
  @Nested
  inner class GenerateTemporaryAbsencesAllPrisonersReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      NomisApiExtension.nomisApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = (1L..3).map { generateOffenderNo(sequence = it) },
      )

      // both same
      nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0001TZ")
      dpsApi.stubGetTapReconciliation(personIdentifier = "A0001TZ")

      // one count different
      nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0002TZ")
      dpsApi.stubGetTapReconciliation(
        personIdentifier = "A0002TZ",
        response = PersonTapCounts(
          authorisations = PersonAuthorisationCount(2),
          occurrences = PersonOccurrenceCount(2),
          movements = PersonMovementsCount(MovementInOutCount(3, 4), MovementInOutCount(5, 6)),
        ),
      )
      // Ignore difference ID calculation
      stubDifferenceIdsEmpty("A0002TZ")

      // all counts different
      nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0003TZ")
      dpsApi.stubGetTapReconciliation(
        personIdentifier = "A0003TZ",
        response = PersonTapCounts(
          authorisations = PersonAuthorisationCount(11),
          occurrences = PersonOccurrenceCount(12),
          movements = PersonMovementsCount(MovementInOutCount(13, 14), MovementInOutCount(15, 16)),
        ),
      )
      // Ignore difference ID calculation
      stubDifferenceIdsEmpty("A0003TZ")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateTapAllPrisonersReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "3")
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("pages-count", "1")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch for a single difference`() = runTest {
      reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0002TZ")
          assertThat(it["type"]).isEqualTo("AUTHORISATIONS")
          assertThat(it["dpsCount"]).isEqualTo("2")
          assertThat(it["nomisCount"]).isEqualTo("1")
        },
        isNull(),
      )
    }

    @Test
    fun `will output multiple mismatches for multiple differences`() = runTest {
      reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0003TZ")
          assertThat(it["type"]).isEqualTo("AUTHORISATIONS")
          assertThat(it["dpsCount"]).isEqualTo("11")
          assertThat(it["nomisCount"]).isEqualTo("1")
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0003TZ")
          assertThat(it["type"]).isEqualTo("OCCURRENCES")
          assertThat(it["dpsCount"]).isEqualTo("12")
          assertThat(it["nomisCount"]).isEqualTo("2")
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0003TZ")
          assertThat(it["type"]).isEqualTo("SCHEDULED_OUT")
          assertThat(it["dpsCount"]).isEqualTo("13")
          assertThat(it["nomisCount"]).isEqualTo("3")
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0003TZ")
          assertThat(it["type"]).isEqualTo("SCHEDULED_IN")
          assertThat(it["dpsCount"]).isEqualTo("14")
          assertThat(it["nomisCount"]).isEqualTo("4")
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0003TZ")
          assertThat(it["type"]).isEqualTo("UNSCHEDULED_OUT")
          assertThat(it["dpsCount"]).isEqualTo("15")
          assertThat(it["nomisCount"]).isEqualTo("5")
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-all-reconciliation-mismatch"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0003TZ")
          assertThat(it["type"]).isEqualTo("UNSCHEDULED_IN")
          assertThat(it["dpsCount"]).isEqualTo("16")
          assertThat(it["nomisCount"]).isEqualTo("6")
        },
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("temporary-absences-all-reconciliation-report"), any(), isNull()) }
    }
  }

  @DisplayName("Temporary absences all prisoners reconciliation report ID differences")
  @Nested
  inner class DifferencesInIds {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      NomisApiExtension.nomisApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = (1L..1).map { generateOffenderNo(sequence = it) },
      )

      // Default all responses to empty so we can override with differences
      stubEverythingEmpty()
    }

    @Nested
    inner class UnexpectedNomisScheduledMovements {
      private val unexpectedApplicationId = 1111L
      private val unexpectedScheduleId = 2222L
      private val unexpectedMovementOutSeq = 3
      private val unexpectedMovementInSeq = 4

      @BeforeEach
      fun `create stubs with unexpected NOMIS application, schedule and movements OUT and IN`() = runTest {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            applications = Applications(1),
            scheduledOutMovements = ScheduledOut(1),
            movements = Movements(
              count = 1,
              scheduled = MovementsByDirection(outCount = 1, inCount = 1),
              unscheduled = MovementsByDirection(outCount = 0, inCount = 0),
            ),
          ),
        )
        nomisApi.stubGetTemporaryAbsencePrisonerSummaryIds(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryIdsResponse().copy(
            applicationIds = listOf(unexpectedApplicationId),
            scheduleOutIds = listOf(unexpectedScheduleId),
            scheduledMovementOutIds = listOf(OffenderTemporaryAbsenceId(12345L, unexpectedMovementOutSeq)),
            scheduledMovementInIds = listOf(OffenderTemporaryAbsenceId(12345L, unexpectedMovementInSeq)),
          ),
        )
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            applications = listOf(TemporaryAbsenceApplicationMappingIdsDto(unexpectedApplicationId, UUID.randomUUID())),
            schedules = listOf(ScheduledMovementMappingIdsDto(unexpectedScheduleId, UUID.randomUUID())),
            movements = listOf(
              ExternalMovementMappingIdsDto(12345L, unexpectedMovementOutSeq, UUID.randomUUID()),
              ExternalMovementMappingIdsDto(12345L, unexpectedMovementInSeq, UUID.randomUUID()),
            ),
          ),
        )

        // Run the reconciliation report
        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS application`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "AUTHORISATIONS",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[$unexpectedApplicationId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS schedule`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "OCCURRENCES",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[$unexpectedScheduleId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS scheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_OUT",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unexpectedMovementOutSeq]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS scheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_IN",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unexpectedMovementInSeq]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class UnexpectedNomisScheduledMovementsWithoutMappings {
      private val unexpectedApplicationId = 1111L
      private val unexpectedScheduleId = 2222L
      private val unexpectedMovementOutSeq = 3
      private val unexpectedMovementInSeq = 4

      @BeforeEach
      fun `create stubs with unexpected NOMIS application, schedule and movements, BUT no mappings`() = runTest {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            applications = Applications(1),
            scheduledOutMovements = ScheduledOut(1),
            movements = Movements(
              count = 1,
              scheduled = MovementsByDirection(outCount = 1, inCount = 1),
              unscheduled = MovementsByDirection(outCount = 0, inCount = 0),
            ),
          ),
        )
        nomisApi.stubGetTemporaryAbsencePrisonerSummaryIds(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryIdsResponse().copy(
            applicationIds = listOf(unexpectedApplicationId),
            scheduleOutIds = listOf(unexpectedScheduleId),
            scheduledMovementOutIds = listOf(OffenderTemporaryAbsenceId(12345L, unexpectedMovementOutSeq)),
            scheduledMovementInIds = listOf(OffenderTemporaryAbsenceId(12345L, unexpectedMovementInSeq)),
          ),
        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS application`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "AUTHORISATIONS",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[$unexpectedApplicationId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS schedule`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "OCCURRENCES",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[$unexpectedScheduleId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS scheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_OUT",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unexpectedMovementOutSeq]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS scheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_IN",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unexpectedMovementInSeq]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class UnexpectedDpsScheduledMovements {
      private val unexpectedAuthorisationId = UUID.randomUUID()
      private val unexpectedOccurrenceId = UUID.randomUUID()
      private val unexpectedMovementOutId = UUID.randomUUID()
      private val unexpectedMovementInId = UUID.randomUUID()

      @BeforeEach
      fun `extra scheduled movement in DPS`() = runTest {
        dpsApi.stubGetTapReconciliation(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapCounts().copy(
            authorisations = PersonAuthorisationCount(1),
            occurrences = PersonOccurrenceCount(1),
            movements = PersonMovementsCount(
              scheduled = MovementInOutCount(1, 1),
              unscheduled = MovementInOutCount(0, 0),
            ),
          ),
        )
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = unexpectedAuthorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = unexpectedOccurrenceId).copy(
                    movements = listOf(
                      reconMovement(id = unexpectedMovementOutId, direction = ReconciliationMovement.Direction.OUT),
                      reconMovement(id = unexpectedMovementInId, direction = ReconciliationMovement.Direction.IN),
                    ),
                  ),
                ),
              ),
            ),
          ),
        )
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            applications = listOf(
              TemporaryAbsenceApplicationMappingIdsDto(
                Random.nextLong(),
                unexpectedAuthorisationId,
              ),
            ),
            schedules = listOf(ScheduledMovementMappingIdsDto(Random.nextLong(), unexpectedOccurrenceId)),
            movements = listOf(
              ExternalMovementMappingIdsDto(12345L, 1, unexpectedMovementOutId),
              ExternalMovementMappingIdsDto(12345L, 2, unexpectedMovementInId),
            ),
          ),
        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra DPS authorisation`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "AUTHORISATIONS",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unexpectedAuthorisationId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS occurrence`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "OCCURRENCES",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unexpectedOccurrenceId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS scheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_OUT",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unexpectedMovementOutId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS scheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "SCHEDULED_IN",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unexpectedMovementInId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class UnexpectedNomisUnscheduledMovements {
      private val unexpectedMovementOutSeq = 1
      private val unexpectedMovementInSeq = 2

      @BeforeEach
      fun `create stubs for unexpected NOMIS unscheduled movements`() = runTest {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            movements = Movements(
              count = 2,
              scheduled = MovementsByDirection(0, 0),
              unscheduled = MovementsByDirection(outCount = 1, inCount = 1),
            ),
          ),
        )
        nomisApi.stubGetTemporaryAbsencePrisonerSummaryIds(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryIdsResponse().copy(
            unscheduledMovementOutIds = listOf(OffenderTemporaryAbsenceId(12345L, unexpectedMovementOutSeq)),
            unscheduledMovementInIds = listOf(OffenderTemporaryAbsenceId(12345L, unexpectedMovementInSeq)),
          ),
        )
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            movements = listOf(
              ExternalMovementMappingIdsDto(12345L, unexpectedMovementOutSeq, UUID.randomUUID()),
              ExternalMovementMappingIdsDto(12345L, unexpectedMovementInSeq, UUID.randomUUID()),
            ),
          ),
        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS unscheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_OUT",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unexpectedMovementOutSeq]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS unscheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_IN",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unexpectedMovementInSeq]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class UnexpectedDpsUnscheduledMovements {
      private val unexpectedMovementOutId = UUID.randomUUID()
      private val unexpectedMovementInId = UUID.randomUUID()

      @BeforeEach
      fun `create stubs for unexpected DPS unscheduled movements`() = runTest {
        dpsApi.stubGetTapReconciliation(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapCounts().copy(
            movements = PersonMovementsCount(
              scheduled = MovementInOutCount(0, 0),
              unscheduled = MovementInOutCount(1, 1),
            ),
          ),
        )
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapDetail().copy(
            unscheduledMovements = listOf(
              reconMovement(id = unexpectedMovementOutId, direction = ReconciliationMovement.Direction.OUT),
              reconMovement(id = unexpectedMovementInId, direction = ReconciliationMovement.Direction.IN),
            ),
          ),
        )
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            movements = listOf(
              ExternalMovementMappingIdsDto(12345L, 1, unexpectedMovementOutId),
              ExternalMovementMappingIdsDto(12345L, 2, unexpectedMovementInId),
            ),
          ),

        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra DPS unscheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_OUT",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unexpectedMovementOutId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS unscheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_IN",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unexpectedMovementInId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class MultipleUnexpectedNomisUnscheduledMovements {
      private val unexpectedMovementOutSeq1 = 1
      private val unexpectedMovementOutSeq2 = 2

      @BeforeEach
      fun `create stubs for unexpected NOMIS unscheduled movements`() = runTest {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            movements = Movements(
              count = 2,
              scheduled = MovementsByDirection(0, 0),
              unscheduled = MovementsByDirection(outCount = 2, inCount = 0),
            ),
          ),
        )
        nomisApi.stubGetTemporaryAbsencePrisonerSummaryIds(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryIdsResponse().copy(
            unscheduledMovementOutIds = listOf(
              OffenderTemporaryAbsenceId(12345L, unexpectedMovementOutSeq1),
              OffenderTemporaryAbsenceId(12345L, unexpectedMovementOutSeq2),
            ),
          ),
        )
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            movements = listOf(
              ExternalMovementMappingIdsDto(12345L, unexpectedMovementOutSeq1, UUID.randomUUID()),
              ExternalMovementMappingIdsDto(12345L, unexpectedMovementOutSeq2, UUID.randomUUID()),
            ),
          ),
        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report all extra NOMIS unscheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_OUT",
              "dpsCount" to "0",
              "nomisCount" to "2",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unexpectedMovementOutSeq1, 12345_$unexpectedMovementOutSeq2]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class MultipleUnexpectedDpsUnscheduledMovements {
      private val unexpectedMovementOutId1 = UUID.randomUUID()
      private val unexpectedMovementOutId2 = UUID.randomUUID()

      @BeforeEach
      fun `create stubs for unexpected DPS unscheduled movements`() = runTest {
        dpsApi.stubGetTapReconciliation(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapCounts().copy(
            movements = PersonMovementsCount(
              scheduled = MovementInOutCount(0, 0),
              unscheduled = MovementInOutCount(2, 0),
            ),
          ),
        )
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapDetail().copy(
            unscheduledMovements = listOf(
              reconMovement(id = unexpectedMovementOutId1, direction = ReconciliationMovement.Direction.OUT),
              reconMovement(id = unexpectedMovementOutId2, direction = ReconciliationMovement.Direction.OUT),
            ),
          ),
        )
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            movements = listOf(
              ExternalMovementMappingIdsDto(12345L, 1, unexpectedMovementOutId1),
              ExternalMovementMappingIdsDto(12345L, 2, unexpectedMovementOutId2),
            ),
          ),

        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra DPS unscheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "UNSCHEDULED_OUT",
              "dpsCount" to "2",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unexpectedMovementOutId1, $unexpectedMovementOutId2]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class MatchingApplicationsNotIncludedNomis {
      private val expectedApplicationId = 1111L
      private val unexpectedApplicationId = 2222L
      private val expectedAuthorisationId = UUID.randomUUID()

      @BeforeEach
      fun `create stubs for unexpected NOMIS unscheduled movements`() = runTest {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            applications = Applications(2),
          ),
        )
        nomisApi.stubGetTemporaryAbsencePrisonerSummaryIds(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryIdsResponse().copy(
            applicationIds = listOf(expectedApplicationId, unexpectedApplicationId),
          ),
        )
        dpsApi.stubGetTapReconciliation(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapCounts().copy(
            authorisations = PersonAuthorisationCount(1),
          ),
        )
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapDetail().copy(
            scheduledAbsences = listOf(reconAuthorisation(id = expectedAuthorisationId)),
          ),
        )
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            applications = listOf(
              TemporaryAbsenceApplicationMappingIdsDto(expectedApplicationId, expectedAuthorisationId),
            ),
          ),
        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS application only`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "AUTHORISATIONS",
              "dpsCount" to "1",
              "nomisCount" to "2",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[$unexpectedApplicationId]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class MatchingApplicationsNotIncludedDps {
      private val expectedApplicationId = 1111L
      private val expectedAuthorisationId = UUID.randomUUID()
      private val unexpectedAuthorisationId = UUID.randomUUID()

      @BeforeEach
      fun `create stubs for unexpected NOMIS unscheduled movements`() = runTest {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            applications = Applications(1),
          ),
        )
        nomisApi.stubGetTemporaryAbsencePrisonerSummaryIds(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryIdsResponse().copy(
            applicationIds = listOf(expectedApplicationId),
          ),
        )
        dpsApi.stubGetTapReconciliation(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapCounts().copy(
            authorisations = PersonAuthorisationCount(2),
          ),
        )
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = expectedAuthorisationId),
              reconAuthorisation(id = unexpectedAuthorisationId),
            ),
          ),
        )
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            applications = listOf(
              TemporaryAbsenceApplicationMappingIdsDto(expectedApplicationId, expectedAuthorisationId),
            ),
          ),
        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS application only`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "AUTHORISATIONS",
              "dpsCount" to "2",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[$unexpectedAuthorisationId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class NomisScheduledInNotReportedAsUnexpected {
      private val nomisScheduleOutId = 1111L
      private val nomisScheduleInId = 2222L

      @BeforeEach
      fun `create stubs for an unexpected OUT and IN schedule in NOMIS`() = runTest {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            scheduledOutMovements = ScheduledOut(1),
          ),
        )
        nomisApi.stubGetTemporaryAbsencePrisonerSummaryIds(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryIdsResponse().copy(
            scheduleOutIds = listOf(nomisScheduleOutId),
            scheduleInIds = listOf(nomisScheduleInId),
          ),
        )

        reconciliationService.generateTapAllPrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS schedule OUT only`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-all-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "type" to "OCCURRENCES",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[$nomisScheduleOutId]",
            ),
          ),
          isNull(),
        )
      }
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("temporary-absences-all-reconciliation-report"), any(), isNull()) }
    }
  }

  @Nested
  inner class SinglePrisonerAllPrisonerReconciliationReport {

    @Nested
    inner class RunReport {
      @Test
      fun `no differences found`() {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0001TZ")
        dpsApi.stubGetTapReconciliation(personIdentifier = "A0001TZ")

        webTestClient.getAllTapsPrisonerReconOk()
          .apply {
            assertThat(this).isEmpty()
          }
      }

      @Test
      fun `one different count`() {
        nomisApi.stubGetTemporaryAbsencePrisonerSummary(offenderNo = "A0001TZ")
        dpsApi.stubGetTapReconciliation(
          personIdentifier = "A0001TZ",
          response = PersonTapCounts(
            authorisations = PersonAuthorisationCount(2),
            occurrences = PersonOccurrenceCount(2),
            movements = PersonMovementsCount(MovementInOutCount(3, 4), MovementInOutCount(5, 6)),
          ),
        )
        stubDifferenceIdsEmpty()

        webTestClient.getAllTapsPrisonerReconOk()
          .apply {
            with(this[0]) {
              assertThat(offenderNo).isEqualTo("A0001TZ")
              assertThat(type).isEqualTo(AUTHORISATIONS)
              assertThat(dpsCount).isEqualTo(2)
              assertThat(nomisCount).isEqualTo(1)
            }
          }
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return error for unknown offender`() {
        webTestClient.getAllTapsPrisonerRecon("UNKNOWN")
          .expectStatus().is5xxServerError
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/external-movements/all-taps/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/external-movements/all-taps/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/external-movements/all-taps/A0001TZ/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    private fun WebTestClient.getAllTapsPrisonerReconOk(offenderNo: String = "A0001TZ") = getAllTapsPrisonerRecon(offenderNo)
      .expectStatus().isOk
      .expectBody<List<MismatchPrisonerTaps>>()
      .returnResult().responseBody!!

    private fun WebTestClient.getAllTapsPrisonerRecon(offenderNo: String = "A0001TZ") = get().uri("/external-movements/all-taps/$offenderNo/reconciliation")
      .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
      .exchange()
  }

  private fun stubEverythingEmpty(offenderNo: String = "A0001TZ") {
    nomisApi.stubGetTemporaryAbsencePrisonerSummary(
      offenderNo = offenderNo,
      response = emptyTemporaryAbsenceSummaryResponse(),
    )
    dpsApi.stubGetTapReconciliation(
      personIdentifier = offenderNo,
      response = emptyPersonTapCounts(),
    )

    stubDifferenceIdsEmpty(offenderNo)
  }

  private fun stubDifferenceIdsEmpty(offenderNo: String = "A0001TZ") {
    nomisApi.stubGetTemporaryAbsencePrisonerSummaryIds(
      offenderNo = offenderNo,
      response = emptyTemporaryAbsenceSummaryIdsResponse(),
    )
    dpsApi.stubGetTapReconciliationDetail(
      personIdentifier = offenderNo,
      response = emptyPersonTapDetail(),
    )
    mappingApi.stubGetTemporaryAbsenceMappingIds(
      prisonerNumber = offenderNo,
      response = emptyPrisonerMappingIdsDto(),
    )
  }
}

private fun emptyTemporaryAbsenceSummaryResponse() = OffenderTemporaryAbsenceSummaryResponse(
  applications = Applications(count = 0),
  scheduledOutMovements = ScheduledOut(count = 0),
  movements = Movements(
    count = 0,
    scheduled = MovementsByDirection(outCount = 0, inCount = 0),
    unscheduled = MovementsByDirection(outCount = 0, inCount = 0),
  ),
)

private fun emptyPersonTapCounts() = PersonTapCounts(
  authorisations = PersonAuthorisationCount(count = 0),
  occurrences = PersonOccurrenceCount(count = 0),
  movements = PersonMovementsCount(
    scheduled = MovementInOutCount(outCount = 0, inCount = 0),
    unscheduled = MovementInOutCount(outCount = 0, inCount = 0),
  ),
)

private fun emptyTemporaryAbsenceSummaryIdsResponse() = OffenderTemporaryAbsenceIdsResponse(
  applicationIds = listOf(),
  scheduleIds = listOf(),
  scheduleOutIds = listOf(),
  scheduleInIds = listOf(),
  scheduledMovementOutIds = listOf(),
  scheduledMovementInIds = listOf(),
  unscheduledMovementOutIds = listOf(),
  unscheduledMovementInIds = listOf(),
)

private fun emptyPersonTapDetail() = PersonTapDetail(
  scheduledAbsences = listOf(),
  unscheduledMovements = listOf(),
)

fun emptyPrisonerMappingIdsDto(offenderNo: String = "A0001TZ") = TemporaryAbsencesPrisonerMappingIdsDto(
  prisonerNumber = offenderNo,
  applications = listOf(),
  schedules = listOf(),
  movements = listOf(),
)
