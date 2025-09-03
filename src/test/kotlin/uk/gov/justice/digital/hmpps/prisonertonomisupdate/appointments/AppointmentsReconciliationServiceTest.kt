package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentAttendeeSearchResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentCategorySummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentSearchResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.RolloutPrisonPlan
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AppointmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AppointmentIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AppointmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

private const val APPOINTMENT_ATTENDEE_ID = 123456789L
private const val NOMIS_EVENT_ID = 4567890L
private const val START_DATE = "2025-08-04"
private const val END_DATE = "2025-08-09"
private const val PRISON_CODE = "MDI"
private const val OFFENDER_NO = "A1234SS"

class AppointmentsReconciliationServiceTest {

  private val appointmentsApiService: AppointmentsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val appointmentsMappingService: AppointmentMappingService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val appointmentsReconciliationService =
    AppointmentsReconciliationService(telemetryClient, nomisApiService, appointmentsMappingService, appointmentsApiService, 5)

  private val appointmentMappingDto = AppointmentMappingDto(
    appointmentInstanceId = APPOINTMENT_ATTENDEE_ID,
    nomisEventId = NOMIS_EVENT_ID,
  )

  @Test
  fun `will iterate over rolled out prisons`() = runTest {
    whenever(appointmentsApiService.getRolloutPrisons()).thenReturn(
      listOf(
        RolloutPrisonPlan(
          prisonCode = PRISON_CODE,
          activitiesRolledOut = true,
          appointmentsRolledOut = true,
          maxDaysToExpiry = 45,
          prisonLive = true,
        ),
        RolloutPrisonPlan(
          prisonCode = "SWI",
          activitiesRolledOut = true,
          appointmentsRolledOut = false,
          maxDaysToExpiry = 45,
          prisonLive = true,
        ),
      ),
    )
    whenever(nomisApiService.getAppointmentIds(eq(listOf(PRISON_CODE)), any(), any(), eq(0), eq(1)))
      .thenReturn(
        PageImpl(
          emptyList(),
          Pageable.ofSize(5),
          0,
        ),
      )
    whenever(appointmentsApiService.searchAppointments(eq(PRISON_CODE), any(), any()))
      .thenReturn(emptyList())

    appointmentsReconciliationService.generateReconciliationReport()

    verify(nomisApiService, times(1)).getAppointmentIds(eq(listOf(PRISON_CODE)), any(), any(), eq(0), eq(1))
    verifyNoMoreInteractions(nomisApiService)
  }

  @Test
  fun `will not report mismatch where details match`() = runTest {
    whenever(nomisApiService.getAppointment(NOMIS_EVENT_ID)).thenReturn(nomisResponse())
    whenever(appointmentsApiService.getAppointmentInstance(APPOINTMENT_ATTENDEE_ID)).thenReturn(dpsResponse(APPOINTMENT_ATTENDEE_ID))

    assertThat(appointmentsReconciliationService.checkMatch(appointmentMappingDto)).isNull()
  }

  @Test
  fun `will report mismatch where appointments have a different offender`() = runTest {
    whenever(nomisApiService.getAppointment(NOMIS_EVENT_ID)).thenReturn(nomisResponse(offenderNo = "A9876ZZ"))
    whenever(appointmentsApiService.getAppointmentInstance(APPOINTMENT_ATTENDEE_ID)).thenReturn(dpsResponse(APPOINTMENT_ATTENDEE_ID))

    val actual = appointmentsReconciliationService.checkMatch(appointmentMappingDto)
    assertThat(actual?.nomisId).isEqualTo(NOMIS_EVENT_ID)
    assertThat(actual?.dpsId).isEqualTo(APPOINTMENT_ATTENDEE_ID)
    assertThat(actual?.nomisAppointment?.offenderNo).isEqualTo("A9876ZZ")
    assertThat(actual?.dpsAppointment?.offenderNo).isEqualTo(OFFENDER_NO)
  }

  @Test
  fun `will continue after a GET mapping error`() {
    runTest {
      whenever(nomisApiService.getAppointmentIds(listOf(PRISON_CODE), LocalDate.parse(START_DATE), LocalDate.parse(END_DATE), 0, 5))
        .thenReturn(
          PageImpl(
            listOf(AppointmentIdResponse(999), AppointmentIdResponse(NOMIS_EVENT_ID)),
            Pageable.ofSize(5),
            2,
          ),
        )
      whenever(appointmentsMappingService.getMappingGivenNomisIdOrNull(999))
        .thenThrow(RuntimeException("test error"))
      whenever(appointmentsMappingService.getMappingGivenNomisIdOrNull(NOMIS_EVENT_ID))
        .thenReturn(AppointmentMappingDto(APPOINTMENT_ATTENDEE_ID, NOMIS_EVENT_ID))

      whenever(nomisApiService.getAppointment(NOMIS_EVENT_ID))
        .thenReturn(nomisResponse().copy(offenderNo = "A9876ZZ"))
      whenever(appointmentsApiService.getAppointmentInstance(APPOINTMENT_ATTENDEE_ID))
        .thenReturn(dpsResponse(APPOINTMENT_ATTENDEE_ID))

      // Total of 2 => no missing dps records
      whenever(appointmentsApiService.searchAppointments(PRISON_CODE, LocalDate.parse(START_DATE), LocalDate.parse(END_DATE))).thenReturn(
        listOf(
          AppointmentSearchResult(
            appointmentSeriesId = 123,
            appointmentId = 12,
            appointmentType = AppointmentSearchResult.AppointmentType.INDIVIDUAL,
            prisonCode = PRISON_CODE,
            appointmentName = "name",
            attendees = listOf(
              AppointmentAttendeeSearchResult(
                appointmentAttendeeId = APPOINTMENT_ATTENDEE_ID,
                prisonerNumber = OFFENDER_NO,
                bookingId = 123,
              ),
              AppointmentAttendeeSearchResult(
                appointmentAttendeeId = 12341234,
                prisonerNumber = OFFENDER_NO,
                bookingId = 123,
              ),
            ),
            category = AppointmentCategorySummary("CODE", "description"),
            inCell = false,
            startDate = LocalDate.parse(START_DATE),
            startTime = "12:12",
            timeSlot = AppointmentSearchResult.TimeSlot.AM,
            isRepeat = false,
            sequenceNumber = 1,
            maxSequenceNumber = 2,
            isEdited = false,
            isCancelled = false,
            isExpired = false,
            createdTime = LocalDateTime.now(),
          ),
        ),
      )

      val results = appointmentsReconciliationService.generateReconciliationReportForPrison(PRISON_CODE, LocalDate.parse(START_DATE), LocalDate.parse(END_DATE), 2)
      assertThat(results).hasSize(2)

      verify(telemetryClient, times(1)).trackEvent(
        eq("appointments-reports-reconciliation-retrieval-error"),
        check {
          assertThat(it).containsEntry("nomis-appointment-id", "999")
        },
        isNull(),
      )
      verify(telemetryClient, times(1)).trackEvent(
        eq("appointments-reports-reconciliation-mismatch"),
        anyMap(),
        isNull(),
      )
      verify(telemetryClient, times(1)).trackEvent(
        eq("appointments-reports-reconciliation-dps-only"),
        anyMap(),
        isNull(),
      )
    }
  }

  private fun nomisResponse(offenderNo: String = OFFENDER_NO, comment: String? = null) = AppointmentResponse(
    bookingId = 123,
    offenderNo = offenderNo,
    prisonId = PRISON_CODE,
    internalLocation = 12345678,
    startDateTime = LocalDateTime.parse("2025-08-05T11:12:00"),
    endDateTime = LocalDateTime.parse("2025-08-05T11:20:00"),
    subtype = "CODE",
    status = "SCH",
    createdDate = LocalDateTime.parse("2025-08-05T10:05:00"),
    createdBy = "ME",
    comment = comment,
  )

  private fun dpsResponse(id: Long, offenderNo: String = OFFENDER_NO, comment: String? = null) = AppointmentInstance(
    id = id,
    appointmentSeriesId = 123,
    appointmentId = 12,
    appointmentAttendeeId = id,
    appointmentType = AppointmentInstance.AppointmentType.INDIVIDUAL,
    prisonCode = PRISON_CODE,
    prisonerNumber = offenderNo,
    bookingId = 123,
    categoryCode = "CODE",
    inCell = false,
    appointmentDate = LocalDate.parse("2025-08-05"),
    startTime = "11:12:00",
    createdTime = LocalDateTime.parse("2025-08-05T10:05:00"),
    createdBy = "ME",
    endTime = "11:20:00",
    internalLocationId = 12345678,
    extraInformation = comment,
  )
}
