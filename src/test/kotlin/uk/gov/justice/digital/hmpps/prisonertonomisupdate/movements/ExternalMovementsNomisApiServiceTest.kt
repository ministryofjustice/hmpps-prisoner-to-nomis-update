package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.upsertScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.upsertTemporaryAbsenceApplicationRequest
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
    inner class UpsertTemporaryAbsenceApplication {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpsertTemporaryAbsenceApplication()

        apiService.upsertTemporaryAbsenceApplication("A1234BC", upsertTemporaryAbsenceApplicationRequest())

        mockServer.verify(
          putRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call upsert endpoint`() = runTest {
        mockServer.stubUpsertTemporaryAbsenceApplication()

        apiService.upsertTemporaryAbsenceApplication("A1234BC", upsertTemporaryAbsenceApplicationRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application"))
            .withRequestBody(
              matchingJsonPath("applicationStatus", equalTo("APP-SCH")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubUpsertTemporaryAbsenceApplication(status = HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.upsertTemporaryAbsenceApplication("A1234BC", upsertTemporaryAbsenceApplicationRequest())
        }
      }
    }
  }

  @Nested
  inner class ScheduledTemporaryAbsence {

    @Nested
    inner class UpsertScheduledTemporaryAbsence {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpsertScheduledTemporaryAbsence()

        apiService.upsertScheduledTemporaryAbsence("A1234BC", upsertScheduledTemporaryAbsenceRequest())

        mockServer.verify(
          putRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubUpsertScheduledTemporaryAbsence()

        apiService.upsertScheduledTemporaryAbsence("A1234BC", upsertScheduledTemporaryAbsenceRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence"))
            .withRequestBody(
              matchingJsonPath("eventSubType", equalTo("C5")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubUpsertScheduledTemporaryAbsence(status = HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.upsertScheduledTemporaryAbsence("A1234BC", upsertScheduledTemporaryAbsenceRequest())
        }
      }
    }
  }

  @Nested
  inner class TemporaryAbsence {

    @Nested
    inner class CreateTemporaryAbsence {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateTemporaryAbsence()

        apiService.createTemporaryAbsence("A1234BC", createTemporaryAbsenceRequest())

        mockServer.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateTemporaryAbsence()

        apiService.createTemporaryAbsence("A1234BC", createTemporaryAbsenceRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence"))
            .withRequestBody(
              matchingJsonPath("movementReason", equalTo("C5")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubCreateTemporaryAbsence(status = HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.createTemporaryAbsence("A1234BC", createTemporaryAbsenceRequest())
        }
      }
    }
  }

  @Nested
  inner class TemporaryAbsenceReturn {

    @Nested
    inner class CreateTemporaryAbsenceReturn {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateTemporaryAbsenceReturn()

        apiService.createTemporaryAbsenceReturn("A1234BC", createTemporaryAbsenceReturnRequest())

        mockServer.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateTemporaryAbsenceReturn()

        apiService.createTemporaryAbsenceReturn("A1234BC", createTemporaryAbsenceReturnRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return"))
            .withRequestBody(
              matchingJsonPath("movementReason", equalTo("C5")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubCreateTemporaryAbsenceReturn(status = HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.createTemporaryAbsenceReturn("A1234BC", createTemporaryAbsenceReturnRequest())
        }
      }
    }
  }

  @Nested
  inner class TemporaryAbsencePrisonerSummary {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetTemporaryAbsencePrisonerSummary()

      apiService.getTemporaryAbsenceSummary("A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      mockServer.stubGetTemporaryAbsencePrisonerSummary()

      apiService.getTemporaryAbsenceSummary("A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/summary")),
      )
    }

    @Test
    fun `will throw if error`() = runTest {
      mockServer.stubGetTemporaryAbsencePrisonerSummary(status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTemporaryAbsenceSummary("A1234BC")
      }
    }
  }

  @Nested
  inner class GetTemporaryAbsencesByBooking {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetBookingTemporaryAbsences(bookingId = 12345)

      apiService.getBookingTemporaryAbsences(bookingId = 12345)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      mockServer.stubGetBookingTemporaryAbsences(bookingId = 12345)

      apiService.getBookingTemporaryAbsences(bookingId = 12345)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/booking/12345/temporary-absences")),
      )
    }

    @Test
    fun `will return temporary absences`() = runTest {
      mockServer.stubGetBookingTemporaryAbsences(bookingId = 12345)

      val result = apiService.getBookingTemporaryAbsences(bookingId = 12345)!!

      assertThat(result.bookingId).isEqualTo(12345L)
      assertThat(result.temporaryAbsenceApplications).hasSize(1)
      assertThat(result.temporaryAbsenceApplications[0].absences[0].scheduledTemporaryAbsence?.eventId).isEqualTo(1)
      assertThat(result.temporaryAbsenceApplications[0].absences[0].scheduledTemporaryAbsenceReturn?.eventId).isEqualTo(2)
      assertThat(result.temporaryAbsenceApplications[0].absences[0].temporaryAbsence?.sequence).isEqualTo(3)
      assertThat(result.temporaryAbsenceApplications[0].absences[0].temporaryAbsenceReturn?.sequence).isEqualTo(4)
      assertThat(result.unscheduledTemporaryAbsences[0].sequence).isEqualTo(1)
      assertThat(result.unscheduledTemporaryAbsenceReturns[0].sequence).isEqualTo(2)
    }

    @Test
    fun `will return null when offender does not exist`() = runTest {
      mockServer.stubGetBookingTemporaryAbsences(bookingId = 12345, status = NOT_FOUND)

      assertThat(apiService.getBookingTemporaryAbsences(bookingId = 12345)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      mockServer.stubGetBookingTemporaryAbsences(bookingId = 12345, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getBookingTemporaryAbsences(bookingId = 12345)
      }
    }
  }
}
