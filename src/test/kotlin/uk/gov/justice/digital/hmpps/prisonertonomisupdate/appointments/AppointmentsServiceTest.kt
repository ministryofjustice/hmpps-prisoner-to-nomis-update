package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.NOMIS_EVENT_ID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AppointmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAppointmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateAppointmentRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentsServiceTest {

  private val appointmentsApiService: AppointmentsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val appointmentsMappingService: AppointmentMappingService = mock()
  private val appointmentsUpdateQueueService: AppointmentsUpdateQueueService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val objectMapper: ObjectMapper = mock()

  private val appointmentsService = AppointmentsService(
    appointmentsApiService,
    nomisApiService,
    appointmentsMappingService,
    appointmentsUpdateQueueService,
    telemetryClient,
    objectMapper,
  )

  @Test
  fun `Comments are constructed correctly 1`() = runTest {
    callService("Appointment description", "Comment")
    verify(nomisApiService).createAppointment(
      check { assertThat(it.comment).isEqualTo("Appointment description - Comment") },
    )
  }

  @Test
  fun `Comments are constructed correctly 2`() = runTest {
    callService("Appointment description", null)
    verify(nomisApiService).createAppointment(
      check { assertThat(it.comment).isEqualTo("Appointment description") },
    )
  }

  @Test
  fun `Comments are constructed correctly 3`() = runTest {
    callService("  ", "Comment")
    verify(nomisApiService).createAppointment(
      check { assertThat(it.comment).isEqualTo("Comment") },
    )
  }

  @Test
  fun `Comments are constructed correctly 4`() = runTest {
    callService(null, "Comment")
    verify(nomisApiService).createAppointment(
      check { assertThat(it.comment).isEqualTo("Comment") },
    )
  }

  @Test
  fun `Comments are constructed correctly 5`() = runTest {
    callService(null, "")
    verify(nomisApiService).createAppointment(
      check { assertThat(it.comment).isNull() },
    )
  }

  @Test
  fun `Comments are constructed correctly - truncated`() = runTest {
    callService("x".repeat(5000), "")
    verify(nomisApiService).createAppointment(
      check { assertThat(it.comment).isEqualTo("x".repeat(4000)) },
    )
  }

  @Test
  fun `Create End time can be null`() = runTest {
    callService(null, null, null)
    verify(nomisApiService).createAppointment(
      check {
        assertThat(it.startTime).isEqualTo("10:15")
        assertThat(it.endTime).isNull()
      },
    )
  }

  @Test
  fun `Update End time can be null`() = runTest {
    whenever(appointmentsApiService.getAppointmentInstance(APPOINTMENT_INSTANCE_ID)).thenReturn(
      newAppointmentInstance(null, null, null),
    )
    whenever(appointmentsMappingService.getMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID)).thenReturn(
      AppointmentMappingDto(APPOINTMENT_INSTANCE_ID, NOMIS_EVENT_ID),
    )

    val captor: ArgumentCaptor<UpdateAppointmentRequest> = ArgumentCaptor.forClass(UpdateAppointmentRequest::class.java)

    val appointment = AppointmentDomainEvent(
      "TYPE",
      "version",
      "description",
      AppointmentAdditionalInformation(appointmentInstanceId = APPOINTMENT_INSTANCE_ID),
    )
    appointmentsService.updateAppointment(appointment)

    captor.apply {
      runBlocking {
        verify(nomisApiService).updateAppointment(
          eq(NOMIS_EVENT_ID),
          capture(captor),
        )
      }
      assertThat(value.startTime).isEqualTo(LocalTime.parse("10:15"))
      assertThat(value.endTime).isNull()
    }
  }

  private suspend fun callService(appointmentDescription: String?, comment: String?, endTime: String? = "11:42") {
    whenever(appointmentsApiService.getAppointmentInstance(APPOINTMENT_INSTANCE_ID)).thenReturn(
      newAppointmentInstance(appointmentDescription, comment, endTime),
    )
    whenever(nomisApiService.createAppointment(any())).thenReturn(CreateAppointmentResponse(eventId = 4567))

    val appointment = AppointmentDomainEvent(
      "TYPE",
      "version",
      "description",
      AppointmentAdditionalInformation(appointmentInstanceId = APPOINTMENT_INSTANCE_ID),
    )
    appointmentsService.createAppointment(appointment)
  }

  private fun newAppointmentInstance(customName: String?, extraInformation: String?, endTime: String?): AppointmentInstance = AppointmentInstance(
    id = APPOINTMENT_INSTANCE_ID,
    appointmentSeriesId = 123,
    appointmentId = 1234,
    appointmentAttendeeId = APPOINTMENT_INSTANCE_ID,
    appointmentType = AppointmentInstance.AppointmentType.INDIVIDUAL,
    bookingId = 12345,
    internalLocationId = 34567,
    appointmentDate = LocalDate.parse("2023-03-14"),
    startTime = "10:15",
    endTime = endTime,
    categoryCode = "DUFF",
    prisonCode = "SKI",
    inCell = false,
    prisonerNumber = "A1234BC",
    createdTime = LocalDateTime.parse("2021-03-14T10:15:00"),
    createdBy = "user1",
    customName = customName,
    extraInformation = "staff information",

    prisonerExtraInformation = extraInformation,
  )
}
