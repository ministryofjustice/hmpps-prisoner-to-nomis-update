@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.github.tomakehurst.wiremock.client.WireMock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension

@SpringAPIServiceTest
@Import(AppointmentsApiService::class, AppointmentsConfiguration::class, RetryApiService::class)
internal class AppointmentApiServiceTest {

  @Autowired
  private lateinit var appointmentsApiService: AppointmentsApiService

  private val appointmentInstanceResponse = """{
    "id": 1234,
    "appointmentSeriesId": 1234,
    "appointmentId": 1234,
    "appointmentAttendeeId": 1234,
    "appointmentType": "INDIVIDUAL",
    "bookingId": 12345,
    "internalLocationId": 34567,
    "dpsLocationId": "17f5a650-f82b-444d-aed3-aef1719cfa8f",
    "appointmentDate": "2023-03-14",
    "startTime": "10:15",
    "endTime":  "11:42",
    "categoryCode": "DUFF",
    "prisonCode": "SKI",
    "inCell": false,
    "prisonerNumber": "A1234BC",
    "cancelled": false,
    "createdTime": "2021-03-14T10:15:00",
    "createdBy": "user1"
  }
  """.trimIndent()

  @Nested
  inner class GetAppointmentInstance {
    @BeforeEach
    internal fun setUp() {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentInstance(1234, appointmentInstanceResponse)
    }

    @Test
    fun `should call api with OAuth2 token`() {
      runTest {
        appointmentsApiService.getAppointmentInstance(1234)

        AppointmentsApiExtension.appointmentsApi.verify(
          WireMock.getRequestedFor(WireMock.urlEqualTo("/appointment-instances/1234"))
            .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }
    }

    @Test
    fun `get parse core data`() {
      runTest {
        val activity = appointmentsApiService.getAppointmentInstance(1234)

        assertThat(activity.id).isEqualTo(1234)
        // TODO assert properties depending on real api DTO
      }
    }

    @Test
    fun `when appointment is not found an exception is thrown`() = runTest {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentInstanceWithError(1234, status = 404)

      assertThrows<NotFound> {
        appointmentsApiService.getAppointmentInstance(1234)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentInstanceWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        appointmentsApiService.getAppointmentInstance(1234)
      }
    }
  }

  @Nested
  inner class GetAppointmentInstanceWithRetries {
    @Test
    fun `should call api with OAuth2 token`() = runTest {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentInstance(1234, appointmentInstanceResponse)

      appointmentsApiService.getAppointmentInstanceWithRetries(1234)

      AppointmentsApiExtension.appointmentsApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/appointment-instances/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `when a repeated transient error occurs retries are attempted but fail`() = runTest {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentInstanceWithError(1234, status = 502)

      assertThrows<IllegalStateException> {
        appointmentsApiService.getAppointmentInstanceWithRetries(1234)
      }
    }

    @Test
    fun `when a temporary transient error occurs retries are attempted and succeed`() = runTest {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentInstanceWithErrorFollowedBySlowSuccess(1234, appointmentInstanceResponse, status = 502)

      assertThat(appointmentsApiService.getAppointmentInstanceWithRetries(1234).id).isEqualTo(1234)
    }
  }
}
