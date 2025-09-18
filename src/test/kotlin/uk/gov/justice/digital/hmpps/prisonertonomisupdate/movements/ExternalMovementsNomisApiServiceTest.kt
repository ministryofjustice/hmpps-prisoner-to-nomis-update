package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceOutsideMovementRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  ExternalMovementsNomisApiService::class,
  ExternalMovementsNomisApiMockServer::class,
  RetryApiService::class,
)
class ExternalMovementsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: ExternalMovementsNomisApiService

  @Autowired
  private lateinit var mockServer: ExternalMovementsNomisApiMockServer

  @Nested
  inner class TemporaryAbsenceApplication {

    @Nested
    inner class CreateTemporaryAbsenceApplication {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateTemporaryAbsenceApplication()

        apiService.createTemporaryAbsenceApplication("A1234BC", createTemporaryAbsenceApplicationRequest())

        mockServer.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateTemporaryAbsenceApplication()

        apiService.createTemporaryAbsenceApplication("A1234BC", createTemporaryAbsenceApplicationRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application"))
            .withRequestBody(
              matchingJsonPath("applicationStatus", equalTo("APP-SCH")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubCreateTemporaryAbsenceApplication(status = HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.createTemporaryAbsenceApplication("A1234BC", createTemporaryAbsenceApplicationRequest())
        }
      }
    }
  }

  @Nested
  inner class TemporaryAbsenceOutsideMovement {

    @Nested
    inner class CreateTemporaryAbsenceOutsideMovement {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateTemporaryAbsenceOutsideMovement()

        apiService.createTemporaryAbsenceOutsideMovement("A1234BC", createTemporaryAbsenceOutsideMovementRequest())

        mockServer.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateTemporaryAbsenceOutsideMovement()

        apiService.createTemporaryAbsenceOutsideMovement("A1234BC", createTemporaryAbsenceOutsideMovementRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/outside-movement"))
            .withRequestBody(
              matchingJsonPath("eventSubType", equalTo("C5")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubCreateTemporaryAbsenceOutsideMovement(status = HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.createTemporaryAbsenceOutsideMovement("A1234BC", createTemporaryAbsenceOutsideMovementRequest())
        }
      }
    }
  }

  @Nested
  inner class ScheduledTemporaryAbsence {

    @Nested
    inner class CreateScheduledTemporaryAbsence {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateScheduledTemporaryAbsence()

        apiService.createScheduledTemporaryAbsence("A1234BC", createScheduledTemporaryAbsenceRequest())

        mockServer.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateScheduledTemporaryAbsence()

        apiService.createScheduledTemporaryAbsence("A1234BC", createScheduledTemporaryAbsenceRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence"))
            .withRequestBody(
              matchingJsonPath("eventSubType", equalTo("C5")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubCreateScheduledTemporaryAbsence(status = HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.createScheduledTemporaryAbsence("A1234BC", createScheduledTemporaryAbsenceRequest())
        }
      }
    }
  }
}
