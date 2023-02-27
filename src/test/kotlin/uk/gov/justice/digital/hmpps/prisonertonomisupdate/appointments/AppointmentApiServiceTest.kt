package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.github.tomakehurst.wiremock.client.WireMock
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
  inner class GetActivity {
    @BeforeEach
    internal fun setUp() {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointment(
        1234,
        """
        {
          "id": 1234,
          "bookingId": 12345,
          "locationId": 34567,
          "date": "2023-03-14",
          "start": "10:15",
          "end":  "11:42",
          "eventSubType": "DUFF"
        }
        """.trimIndent()
      )
    }

    @Test
    fun `should call api with OAuth2 token`() {
      runBlocking {
        appointmentsApiService.getAppointment(1234)

        AppointmentsApiExtension.appointmentsApi.verify(
          WireMock.getRequestedFor(WireMock.urlEqualTo("/appointments/1234"))
            .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
        )
      }
    }

    @Test
    fun `get parse core data`() {
      runBlocking {
        val activity = appointmentsApiService.getAppointment(1234)

        assertThat(activity.id).isEqualTo(1234)
        // TODO assert properties depending on real api DTO
      }
    }

    @Test
    fun `when schedule is not found an exception is thrown`() {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentWithError(1234, status = 404)

      Assertions.assertThatThrownBy {
        runBlocking { appointmentsApiService.getAppointment(1234) }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      AppointmentsApiExtension.appointmentsApi.stubGetAppointmentWithError(1234, status = 503)

      Assertions.assertThatThrownBy {
        runBlocking { appointmentsApiService.getAppointment(1234) }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }
}
