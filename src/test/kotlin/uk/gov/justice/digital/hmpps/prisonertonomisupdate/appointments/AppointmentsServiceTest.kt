package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAppointmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
internal class AppointmentsServiceTest {

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
    callService(null, "Comment")
    verify(nomisApiService).createAppointment(
      check { assertThat(it.comment).isEqualTo("Comment") },
    )
  }

  @Test
  fun `Comments are constructed correctly 4`() = runTest {
    callService(null, null)
    verify(nomisApiService).createAppointment(
      check { assertThat(it.comment).isNull() },
    )
  }

  private suspend fun callService(appointmentDescription: String?, comment: String?) {
    whenever(appointmentsApiService.getAppointmentInstance(APPOINTMENT_INSTANCE_ID)).thenReturn(
      newAppointment(appointmentDescription, comment),
    )
    whenever(nomisApiService.createAppointment(any())).thenReturn(CreateAppointmentResponse(eventId = 4567))

    val appointment = AppointmentDomainEvent(
      "TYPE",
      "version",
      "description",
      LocalDateTime.now(),
      AppointmentAdditionalInformation(appointmentInstanceId = APPOINTMENT_INSTANCE_ID),
    )
    appointmentsService.createAppointment(appointment)
  }

  private fun newAppointment(appointmentDescription: String?, comment: String?): AppointmentInstance =
    AppointmentInstance(
      id = APPOINTMENT_INSTANCE_ID,
      appointmentOccurrenceAllocationId = 12345,
      appointmentOccurrenceId = 1234,
      appointmentId = 123,
      appointmentType = AppointmentInstance.AppointmentType.INDIVIDUAL,
      bookingId = 12345,
      internalLocationId = 34567,
      appointmentDate = LocalDate.parse("2023-03-14"),
      startTime = "10:15",
      endTime = "11:42",
      categoryCode = "DUFF",
      prisonCode = "SKI",
      inCell = false,
      prisonerNumber = "A1234BC",
      created = LocalDateTime.parse("2021-03-14T10:15:00"),
      createdBy = "user1",
      appointmentDescription = appointmentDescription,
      comment = comment,
    )
}
