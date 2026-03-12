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
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.absence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.application
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.scheduledAbsence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.scheduledAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.temporaryAbsence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.temporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTemporaryAbsences
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.util.UUID

class TemporaryAbsencesActivePrisonersReconciliationIntTest(
  @Autowired private val reconciliationService: TemporaryAbsencesActivePrisonersReconciliationService,
  @Autowired private val nomisMovementsApi: ExternalMovementsNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = ExternalMovementsDpsApiExtension.dpsExternalMovementsServer
  private val nomisApi = NomisApiExtension.nomisApi

  @DisplayName("Temporary absences active prisoners reconciliation report")
  @Nested
  inner class GenerateTemporaryAbsencesActivePrisonersReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stuGetAllLatestBookings(
        activeOnly = true,
        response = BookingIdsWithLast(
          lastBookingId = 12345,
          prisonerIds = listOf(PrisonerIds(offenderNo = "A0001TZ", bookingId = 12345)),
        ),
      )

      nomisMovementsApi.stubGetTemporaryAbsences(offenderNo = "A0001TZ")
      dpsApi.stubGetTapReconciliationDetail(personIdentifier = "A0001TZ")
      mappingApi.stubGetTemporaryAbsenceMappingIds(prisonerNumber = "A0001TZ")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateTapActivePrisonersReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-active-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-active-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "1")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("pages-count", "1")
        },
        isNull(),
      )
    }
  }

  @DisplayName("Active Detail Reconciliation Different Ids")
  @Nested
  inner class ActivePrisonersReconciliationDifferentIds {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stuGetAllLatestBookings(
        activeOnly = true,
        response = BookingIdsWithLast(
          lastBookingId = 12345,
          prisonerIds = listOf(PrisonerIds(offenderNo = "A0001TZ", bookingId = 12345)),
        ),
      )

      stubDifferenceIdsEmpty()
    }

    @Nested
    inner class UnexpectedTapsInNomis {
      private val applicationId = 1111L
      private val scheduleOutId = 2222L
      private val scheduleInId = 9999L
      private val scheduledMovementOutSeq = 3
      private val scheduledMovementInSeq = 4
      private val unscheduledMovementOutSeq = 5
      private val unscheduledMovementInSeq = 6

      @BeforeEach
      fun setUp() = runTest {
        nomisMovementsApi.stubGetTemporaryAbsences(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            bookings = listOf(
              BookingTemporaryAbsences(
                bookingId = 12345,
                temporaryAbsenceApplications = listOf(
                  application(
                    id = applicationId,
                    absences = listOf(
                      absence(
                        scheduledAbsence = scheduledAbsence(id = scheduleOutId),
                        scheduledAbsenceReturn = scheduledAbsenceReturn(id = scheduleInId),
                        temporaryAbsence = temporaryAbsence(seq = scheduledMovementOutSeq),
                        temporaryAbsenceReturn = temporaryAbsenceReturn(seq = scheduledMovementInSeq),
                      ),
                    ),
                  ),
                ),
                unscheduledTemporaryAbsences = listOf(temporaryAbsence(seq = unscheduledMovementOutSeq)),
                unscheduledTemporaryAbsenceReturns = listOf(temporaryAbsenceReturn(seq = unscheduledMovementInSeq)),
                activeBooking = true,
                latestBooking = true,
              ),
            ),
          ),
        )

        // Only include some of the mappings to prove that missing mappings behaves the same as missing in DPS
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            applications = listOf(TemporaryAbsenceApplicationMappingIdsDto(applicationId, UUID.randomUUID())),
            schedules = listOf(ScheduledMovementMappingIdsDto(scheduleOutId, UUID.randomUUID())),
          ),
        )

        // Run the reconciliation report
        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra NOMIS application`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "AUTHORISATIONS",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[$applicationId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS schedule`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "OCCURRENCES",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[$scheduleOutId]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS scheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "SCHEDULED_OUT",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$scheduledMovementOutSeq]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS scheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "SCHEDULED_IN",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$scheduledMovementInSeq]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS unscheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "UNSCHEDULED_OUT",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unscheduledMovementOutSeq]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra NOMIS unscheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "UNSCHEDULED_IN",
              "dpsCount" to "0",
              "nomisCount" to "1",
              "unexpected-dps-ids" to "[]",
              "unexpected-nomis-ids" to "[12345_$unscheduledMovementInSeq]",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class UnexpectedTapsInDps {
      private val authorisationId = UUID.randomUUID()
      private val occurrenceId = UUID.randomUUID()
      private val scheduledMovementOutId = UUID.randomUUID()
      private val scheduledMovementInId = UUID.randomUUID()
      private val unscheduledMovementOutId = UUID.randomUUID()
      private val unscheduledMovementInId = UUID.randomUUID()

      @BeforeEach
      fun setUp() = runTest {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = emptyPersonTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    movements = listOf(
                      reconMovement(id = scheduledMovementOutId, direction = ReconciliationMovement.Direction.OUT),
                      reconMovement(id = scheduledMovementInId, direction = ReconciliationMovement.Direction.IN),
                    ),
                  ),
                ),
              ),
            ),
            unscheduledMovements = listOf(
              reconMovement(id = unscheduledMovementOutId, direction = ReconciliationMovement.Direction.OUT),
              reconMovement(id = unscheduledMovementInId, direction = ReconciliationMovement.Direction.IN),
            ),
          ),
        )

        // Only include some of the mappings to prove that missing mappings behaves the same as missing in NOMIS
        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            applications = listOf(TemporaryAbsenceApplicationMappingIdsDto(1111L, authorisationId)),
            schedules = listOf(ScheduledMovementMappingIdsDto(2222L, occurrenceId)),
          ),
        )

        // Run the reconciliation report
        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()
      }

      @Test
      fun `should report extra DPS authorisation`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "AUTHORISATIONS",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$authorisationId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS occurrence`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "OCCURRENCES",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$occurrenceId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS scheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "SCHEDULED_OUT",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$scheduledMovementOutId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS scheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "SCHEDULED_IN",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$scheduledMovementInId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS unscheduled movement OUT`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "UNSCHEDULED_OUT",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unscheduledMovementOutId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report extra DPS unscheduled movement IN`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "bookingId" to "12345",
              "type" to "UNSCHEDULED_IN",
              "dpsCount" to "1",
              "nomisCount" to "0",
              "unexpected-dps-ids" to "[$unscheduledMovementInId]",
              "unexpected-nomis-ids" to "[]",
            ),
          ),
          isNull(),
        )
      }
    }
  }

  private fun stubDifferenceIdsEmpty(offenderNo: String = "A0001TZ") {
    nomisMovementsApi.stubGetTemporaryAbsences(
      offenderNo = offenderNo,
      response = emptyTemporaryAbsenceSummaryResponse(),
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

  @DisplayName("Active prisoners reconciliation report errors")
  @Nested
  inner class ActivePrisonersReconciliationReportErrors {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stuGetAllLatestBookings(
        activeOnly = true,
        response = BookingIdsWithLast(
          lastBookingId = 12345,
          prisonerIds = listOf(PrisonerIds(offenderNo = "A0001TZ", bookingId = 12345)),
        ),
      )

      nomisMovementsApi.stubGetTemporaryAbsences(offenderNo = "A0001TZ")
      dpsApi.stubGetTapReconciliationDetail(personIdentifier = "A0001TZ")
      mappingApi.stubGetTemporaryAbsenceMappingIds(prisonerNumber = "A0001TZ")
    }

    @Test
    fun `will report error if NOMIS prisoner not found`() = runTest {
      nomisMovementsApi.stubGetTemporaryAbsences(status = HttpStatus.NOT_FOUND)

      reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("temporary-absences-active-reconciliation-mismatch-error"),
        eq(
          mapOf(
            "offenderNo" to "A0001TZ",
            "bookingId" to "12345",
            "reason" to "Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?",
          ),
        ),
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("temporary-absences-active-reconciliation-report"), any(), isNull()) }
  }
}

private fun emptyTemporaryAbsenceSummaryResponse(bookingId: Long = 12345L) = OffenderTemporaryAbsencesResponse(
  bookings = listOf(
    BookingTemporaryAbsences(
      bookingId = bookingId,
      temporaryAbsenceApplications = listOf(),
      unscheduledTemporaryAbsences = listOf(),
      unscheduledTemporaryAbsenceReturns = listOf(),
      latestBooking = true,
      activeBooking = true,
    ),
  ),
)

private fun emptyPersonTapDetail() = PersonTapDetail(
  scheduledAbsences = listOf(),
  unscheduledMovements = listOf(),
)

private fun emptyPrisonerMappingIdsDto(offenderNo: String = "A0001TZ") = TemporaryAbsencesPrisonerMappingIdsDto(
  prisonerNumber = offenderNo,
  applications = listOf(),
  schedules = listOf(),
  movements = listOf(),
)
