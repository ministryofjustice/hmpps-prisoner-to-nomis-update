package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension

@SpringAPIServiceTest
@Import(AppointmentMappingService::class)
internal class AppointmentsMappingServiceTest {

  @Autowired
  private lateinit var mappingService: AppointmentMappingService

  @Nested
  inner class CreateMapping {
    @BeforeEach
    internal fun setUp() {
      MappingExtension.mappingServer.stubCreateAppointment()
    }

    @Test
    fun `should call mapping api with OAuth2 token`() {
      runBlocking { mappingService.createMapping(newMapping()) }
      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/appointments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to mapping api`() {
      runBlocking { mappingService.createMapping(newMapping()) }

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/appointments"))
          .withRequestBody(matchingJsonPath("$.nomisEventId", equalTo("456")))
      )
    }

    @Test
    fun `when a bad response is received an exception is thrown`() {
      MappingExtension.mappingServer.stubCreateAppointmentWithError(400)

      assertThatThrownBy {
        runBlocking { mappingService.createMapping(newMapping()) }
      }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class GetMappingGivenAppointmentInstanceId {

    @Test
    fun `should call api with OAuth2 token`() {
      MappingExtension.mappingServer.stubGetMappingGivenAppointmentInstanceId(
        id = 1234,
        response = """{
          "nomisEventId": 456,
          "appointmentInstanceId": 1234
        }""".trimMargin(),
      )

      runBlocking { mappingService.getMappingGivenAppointmentInstanceId(1234) }

      MappingExtension.mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will return data`() {
      MappingExtension.mappingServer.stubGetMappingGivenAppointmentInstanceId(
        id = 1234,
        response = """{
          "nomisEventId": 456,
          "appointmentInstanceId": 1234
        }""".trimMargin(),
      )

      runBlocking {
        val data = mappingService.getMappingGivenAppointmentInstanceId(1234)

        assertThat(data).isEqualTo(newMapping())
      }
    }

    @Test
    fun `when mapping is not found null is returned`() {
      MappingExtension.mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(123, 404)

      assertThat(
        runBlocking {
          mappingService.getMappingGivenAppointmentInstanceIdOrNull(123)
        }
      ).isNull()
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      MappingExtension.mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(123, 503)

      assertThatThrownBy {
        runBlocking { mappingService.getMappingGivenAppointmentInstanceId(123) }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  private fun newMapping() =
    AppointmentMappingDto(nomisEventId = 456L, appointmentInstanceId = 1234L)
}
