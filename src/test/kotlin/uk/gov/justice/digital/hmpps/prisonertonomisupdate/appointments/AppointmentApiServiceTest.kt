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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension

@SpringAPIServiceTest
@Import(AppointmentsApiService::class, AppointmentsConfiguration::class)
internal class AppointmentApiServiceTest {

  @Autowired
  private lateinit var appointmentsApiService: AppointmentsApiService

  @Nested
  inner class GetAppointmentInstance {

    private val appointmentResponse = """{
      "id": 1234,
      "bookingId": 12345,
      "internalLocationId": 34567,
      "appointmentDate": "2023-03-14",
      "startTime": "10:15",
      "endTime":  "11:42",
      "category": {
        "id": 1919,
        "active": true,
        "code": "DUFF",
        "description": "Medical - Initial assessment"
      },
      "prisonCode": "SKI",
      "inCell": false,
      "prisonerNumber": "A1234BC",
      "cancelled": false
    }
    """.trimIndent()

    @BeforeEach
    internal fun setUp() {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentInstance(1234, appointmentResponse)
    }

    @Test
    fun `should call api with OAuth2 token`() {
      runTest {
        appointmentsApiService.getAppointmentInstance(1234)

        AppointmentsApiExtension.appointmentsApi.verify(
          WireMock.getRequestedFor(WireMock.urlEqualTo("/appointment-instance-details/1234"))
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
    fun `when schedule is not found an exception is thrown`() = runTest {
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
}
