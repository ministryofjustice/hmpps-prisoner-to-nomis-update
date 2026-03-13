package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.personTapDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiMockServer.Companion.reconOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.absence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.application
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.scheduledAbsence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.scheduledAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.temporaryAbsence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.temporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTemporaryAbsences
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.time.LocalDateTime
import java.util.*

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

  @Nested
  @DisplayName("Active Detail Reconciliation Different Occurrence details")
  inner class ActivePrisonersReconciliationDifferentOccurrenceDetails {
    private val applicationId = 1111L
    private val scheduleOutId = 2222L
    private val authorisationId = UUID.randomUUID()
    private val occurrenceId = UUID.randomUUID()

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
    }

    @Nested
    inner class ReportDifferences {
      val startTime = LocalDateTime.now().plusDays(1)
      val endTime = LocalDateTime.now().plusDays(2)

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
                        scheduledAbsence = scheduledAbsence(id = scheduleOutId).copy(
                          eventStatus = "SCH",
                          eventSubType = "C5",
                          startTime = startTime,
                          returnTime = endTime,
                          toAddressPostcode = "S1 1AA",
                        ),
                        scheduledAbsenceReturn = null,
                        temporaryAbsence = null,
                        temporaryAbsenceReturn = null,
                      ),
                    ),
                  ),
                ),
                activeBooking = true,
                latestBooking = true,
                unscheduledTemporaryAbsences = listOf(),
                unscheduledTemporaryAbsenceReturns = listOf(),
              ),
            ),
          ),
        )

        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    statusCode = ReconciliationOccurrence.StatusCode.SCHEDULED,
                    reasonCode = "C5",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                    movements = listOf(),
                  ),
                ),
              ),
            ),
            unscheduledMovements = listOf(),
          ),
        )

        mappingApi.stubGetTemporaryAbsenceMappingIds(
          prisonerNumber = "A0001TZ",
          response = emptyPrisonerMappingIdsDto().copy(
            applications = listOf(TemporaryAbsenceApplicationMappingIdsDto(applicationId, authorisationId)),
            schedules = listOf(ScheduledMovementMappingIdsDto(scheduleOutId, occurrenceId)),
          ),
        )
      }

      @Test
      fun `should not publish telemetry if no differences`() = runTest {
        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient, never()).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          anyMap(),
          isNull(),
        )
      }

      @Test
      fun `should report status is different`() = runTest {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    statusCode = ReconciliationOccurrence.StatusCode.IN_PROGRESS,
                    reasonCode = "C5",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                  ),
                ),
              ),
            ),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "$scheduleOutId",
              "dpsOccurrenceId" to "$occurrenceId",
              "type" to "STATUS",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report reason code is different`() = runTest {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    statusCode = ReconciliationOccurrence.StatusCode.SCHEDULED,
                    reasonCode = "RC",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                  ),
                ),
              ),
            ),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "$scheduleOutId",
              "dpsOccurrenceId" to "$occurrenceId",
              "type" to "REASON",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report start time is different`() = runTest {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    statusCode = ReconciliationOccurrence.StatusCode.SCHEDULED,
                    reasonCode = "C5",
                    start = LocalDateTime.now(),
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                  ),
                ),
              ),
            ),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "$scheduleOutId",
              "dpsOccurrenceId" to "$occurrenceId",
              "type" to "START_TIME",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report end time is different`() = runTest {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    statusCode = ReconciliationOccurrence.StatusCode.SCHEDULED,
                    reasonCode = "C5",
                    start = startTime,
                    end = LocalDateTime.now().plusDays(3),
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                  ),
                ),
              ),
            ),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "$scheduleOutId",
              "dpsOccurrenceId" to "$occurrenceId",
              "type" to "END_TIME",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report postcode is different`() = runTest {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    statusCode = ReconciliationOccurrence.StatusCode.SCHEDULED,
                    reasonCode = "C5",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "X1 1XX",
                    ),
                  ),
                ),
              ),
            ),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "$scheduleOutId",
              "dpsOccurrenceId" to "$occurrenceId",
              "type" to "POSTCODE",
            ),
          ),
          isNull(),
        )
      }

      @Test
      fun `should report everything is different`() = runTest {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    statusCode = ReconciliationOccurrence.StatusCode.IN_PROGRESS,
                    reasonCode = "RC",
                    start = LocalDateTime.now(),
                    end = LocalDateTime.now().plusDays(3),
                    location = Location(
                      address = "1 street",
                      postcode = "X1 1XX",
                    ),
                  ),
                ),
              ),
            ),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient, times(5)).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001TZ")
            assertThat(it["nomisEventId"]).isEqualTo("$scheduleOutId")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$occurrenceId")
          },
          isNull(),
        )
      }

      @Test
      fun `should not publish telemetry if only difference is Expired on an old booking`() = runTest {
        nomisMovementsApi.stubGetTemporaryAbsences(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            bookings = listOf(
              BookingTemporaryAbsences(
                bookingId = 12345,
                // important - NOT the latest booking
                latestBooking = false,
                activeBooking = false,
                temporaryAbsenceApplications = listOf(
                  application(
                    id = applicationId,
                    absences = listOf(
                      absence(
                        scheduledAbsence = scheduledAbsence(id = scheduleOutId).copy(
                          eventStatus = "SCH",
                          eventSubType = "C5",
                          startTime = startTime,
                          returnTime = endTime,
                          toAddressPostcode = "S1 1AA",
                        ),
                        scheduledAbsenceReturn = null,
                        temporaryAbsence = null,
                        temporaryAbsenceReturn = null,
                      ),
                    ),
                  ),
                ),
                unscheduledTemporaryAbsences = listOf(),
                unscheduledTemporaryAbsenceReturns = listOf(),
              ),
            ),
          ),
        )

        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    // important - expired in DPS
                    statusCode = ReconciliationOccurrence.StatusCode.EXPIRED,
                    reasonCode = "C5",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                    movements = listOf(),
                  ),
                ),
              ),
            ),
            unscheduledMovements = listOf(),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient, never()).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          anyMap(),
          isNull(),
        )
      }

      @Test
      fun `should NOT publish telemetry if NOMIS expired but DPS cancelled`() = runTest {
        nomisMovementsApi.stubGetTemporaryAbsences(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            bookings = listOf(
              BookingTemporaryAbsences(
                bookingId = 12345,
                latestBooking = true,
                activeBooking = true,
                temporaryAbsenceApplications = listOf(
                  application(
                    id = applicationId,
                    absences = listOf(
                      absence(
                        scheduledAbsence = scheduledAbsence(id = scheduleOutId).copy(
                          // important - expired in NOMIS
                          eventStatus = "EXP",
                          eventSubType = "C5",
                          startTime = startTime,
                          returnTime = endTime,
                          toAddressPostcode = "S1 1AA",
                        ),
                        scheduledAbsenceReturn = null,
                        temporaryAbsence = null,
                        temporaryAbsenceReturn = null,
                      ),
                    ),
                  ),
                ),
                unscheduledTemporaryAbsences = listOf(),
                unscheduledTemporaryAbsenceReturns = listOf(),
              ),
            ),
          ),
        )

        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    // important - cancelled in DPS
                    statusCode = ReconciliationOccurrence.StatusCode.CANCELLED,
                    reasonCode = "C5",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                    movements = listOf(),
                  ),
                ),
              ),
            ),
            unscheduledMovements = listOf(),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient, never()).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          anyMap(),
          isNull(),
        )
      }

      @Test
      fun `should publish telemetry if Expired, old booking but NOMIS scheduled is completed`() = runTest {
        nomisMovementsApi.stubGetTemporaryAbsences(
          offenderNo = "A0001TZ",
          response = emptyTemporaryAbsenceSummaryResponse().copy(
            bookings = listOf(
              BookingTemporaryAbsences(
                bookingId = 12345,
                // important - NOT the latest booking
                latestBooking = false,
                activeBooking = false,
                temporaryAbsenceApplications = listOf(
                  application(
                    id = applicationId,
                    absences = listOf(
                      absence(
                        scheduledAbsence = scheduledAbsence(id = scheduleOutId).copy(
                          // important - schedule in NOMIS is completed
                          eventStatus = "COMP",
                          eventSubType = "C5",
                          startTime = startTime,
                          returnTime = endTime,
                          toAddressPostcode = "S1 1AA",
                        ),
                        scheduledAbsenceReturn = null,
                        temporaryAbsence = null,
                        temporaryAbsenceReturn = null,
                      ),
                    ),
                  ),
                ),
                unscheduledTemporaryAbsences = listOf(),
                unscheduledTemporaryAbsenceReturns = listOf(),
              ),
            ),
          ),
        )

        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    // important - expired in DPS
                    statusCode = ReconciliationOccurrence.StatusCode.EXPIRED,
                    reasonCode = "C5",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                    movements = listOf(),
                  ),
                ),
              ),
            ),
            unscheduledMovements = listOf(),
          ),
        )

        reconciliationService.generateTapActivePrisonersReconciliationReportBatch()
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("temporary-absences-active-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to "A0001TZ",
              "nomisEventId" to "$scheduleOutId",
              "dpsOccurrenceId" to "$occurrenceId",
              "type" to "STATUS",
            ),
          ),
          isNull(),
        )
      }
    }
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
            "reason" to "Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?",
          ),
        ),
        isNull(),
      )
    }
  }

  @Nested
  inner class SinglePrisonerActivePrisonerReconciliationReport {
    private val applicationId = 1111L
    private val scheduleOutId = 2222L
    private val authorisationId = UUID.randomUUID()
    private val occurrenceId = UUID.randomUUID()
    private val dpsMovementId = UUID.randomUUID()
    private val startTime = LocalDateTime.now().plusDays(1)
    private val endTime = LocalDateTime.now().plusDays(2)

    @BeforeEach
    fun setUp() = runTest {
      nomisMovementsApi.stubGetTemporaryAbsences(
        offenderNo = "A0001TZ",
        response = emptyTemporaryAbsenceSummaryResponse()
          .copy(
            bookings = listOf(
              BookingTemporaryAbsences(
                bookingId = 12345,
                temporaryAbsenceApplications = listOf(
                  application(
                    id = applicationId,
                    absences = listOf(
                      absence(
                        scheduledAbsence = scheduledAbsence(id = scheduleOutId).copy(
                          eventStatus = "SCH",
                          eventSubType = "C5",
                          startTime = startTime,
                          returnTime = endTime,
                          toAddressPostcode = "S1 1AA",
                        ),
                        scheduledAbsenceReturn = null,
                        temporaryAbsence = null,
                        temporaryAbsenceReturn = null,
                      ),
                    ),
                  ),
                ),
                activeBooking = true,
                latestBooking = true,
                unscheduledTemporaryAbsences = listOf(),
                unscheduledTemporaryAbsenceReturns = listOf(),
              ),
            ),
          ),
      )

      dpsApi.stubGetTapReconciliationDetail(
        personIdentifier = "A0001TZ",
        response = personTapDetail().copy(
          scheduledAbsences = listOf(
            reconAuthorisation(id = authorisationId).copy(
              occurrences = listOf(
                reconOccurrence(id = occurrenceId).copy(
                  statusCode = ReconciliationOccurrence.StatusCode.SCHEDULED,
                  reasonCode = "C5",
                  start = startTime,
                  end = endTime,
                  location = Location(
                    address = "1 street",
                    postcode = "S1 1AA",
                  ),
                  movements = listOf(),
                ),
              ),
            ),
          ),
          unscheduledMovements = listOf(),
        ),
      )

      mappingApi.stubGetTemporaryAbsenceMappingIds(
        prisonerNumber = "A0001TZ",
        response = emptyPrisonerMappingIdsDto().copy(
          applications = listOf(TemporaryAbsenceApplicationMappingIdsDto(applicationId, authorisationId)),
          schedules = listOf(ScheduledMovementMappingIdsDto(scheduleOutId, occurrenceId)),
        ),
      )
    }

    @Nested
    inner class RunReport {
      @Test
      fun `no differences found`() {
        webTestClient.getActiveTapsPrisonerReconOk()
          .expectBody()
          .jsonPath("$.length()").isEqualTo(0)
      }

      @Test
      fun `one detail difference`() {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    // status is different to NOMIS
                    statusCode = ReconciliationOccurrence.StatusCode.IN_PROGRESS,
                    reasonCode = "C5",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                    movements = listOf(),
                  ),
                ),
              ),
            ),
            unscheduledMovements = listOf(),
          ),
        )

        webTestClient.getActiveTapsPrisonerReconOk()
          .expectBody()
          .jsonPath("$.length()").isEqualTo(1)
          .jsonPath("$[0].offenderNo").isEqualTo("A0001TZ")
          .jsonPath("$[0].nomisEventId").isEqualTo("$scheduleOutId")
          .jsonPath("$[0].dpsOccurrenceId").isEqualTo("$occurrenceId")
          .jsonPath("$[0].nomisValue").isEqualTo("SCH")
          .jsonPath("$[0].dpsValue").isEqualTo("IN_PROGRESS")
      }

      @Test
      fun `one count difference`() {
        dpsApi.stubGetTapReconciliationDetail(
          personIdentifier = "A0001TZ",
          response = personTapDetail().copy(
            scheduledAbsences = listOf(
              reconAuthorisation(id = authorisationId).copy(
                occurrences = listOf(
                  reconOccurrence(id = occurrenceId).copy(
                    statusCode = ReconciliationOccurrence.StatusCode.SCHEDULED,
                    reasonCode = "C5",
                    start = startTime,
                    end = endTime,
                    location = Location(
                      address = "1 street",
                      postcode = "S1 1AA",
                    ),
                    movements = listOf(
                      reconMovement(id = dpsMovementId),
                    ),
                  ),
                ),
              ),
            ),
            unscheduledMovements = listOf(),
          ),
        )

        webTestClient.getActiveTapsPrisonerReconOk()
          .expectBody()
          .jsonPath("$.length()").isEqualTo(1)
          .jsonPath("$[0].offenderNo").isEqualTo("A0001TZ")
          .jsonPath("$[0].type").isEqualTo("SCHEDULED_OUT")
          .jsonPath("$[0].dpsCount").isEqualTo("1")
          .jsonPath("$[0].nomisCount").isEqualTo("0")
          .jsonPath("$[0].unexpectedNomisIds").isEqualTo("[]")
          .jsonPath("$[0].unexpectedDpsIds").isEqualTo("[$dpsMovementId]")
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return error for unknown offender`() {
        webTestClient.getActiveTapsPrisonerRecon("UNKNOWN")
          .expectStatus().is5xxServerError
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/external-movements/active-taps/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/external-movements/active-taps/A0001TZ/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/external-movements/active-taps/A0001TZ/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    private fun WebTestClient.getActiveTapsPrisonerReconOk(
      offenderNo: String = "A0001TZ",
    ) = getActiveTapsPrisonerRecon(offenderNo)
      .expectStatus().isOk

    private fun WebTestClient.getActiveTapsPrisonerRecon(
      offenderNo: String = "A0001TZ",
    ) = get().uri("/external-movements/active-taps/$offenderNo/reconciliation")
      .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
      .exchange()
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
