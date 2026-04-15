package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapNomisApiMockServer.Companion.createTapMovementIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapNomisApiMockServer.Companion.createTapMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapNomisApiMockServer.Companion.upsertTapApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapNomisApiMockServer.Companion.upsertTapScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  TapNomisApiService::class,
  TapNomisApiMockServer::class,
  RetryApiService::class,
)
class TapNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: TapNomisApiService

  @Autowired
  private lateinit var mockServer: TapNomisApiMockServer

  @Nested
  inner class TapApplication {

    @Nested
    inner class UpsertTapApplicationRequest {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpsertTapApplication()

        apiService.upsertTapApplication("A1234BC", upsertTapApplicationRequest())

        mockServer.verify(
          putRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call upsert endpoint`() = runTest {
        mockServer.stubUpsertTapApplication()

        apiService.upsertTapApplication("A1234BC", upsertTapApplicationRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application"))
            .withRequestBody(
              matchingJsonPath("applicationStatus", equalTo("APP-SCH")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubUpsertTapApplication(status = INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.upsertTapApplication("A1234BC", upsertTapApplicationRequest())
        }
      }
    }
  }

  @Nested
  inner class TapScheduleOutTest {

    @Nested
    inner class UpsertTapScheduleOut {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpsertTapScheduleOut()

        apiService.upsertTapScheduleOut("A1234BC", upsertTapScheduleOut())

        mockServer.verify(
          putRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubUpsertTapScheduleOut()

        apiService.upsertTapScheduleOut("A1234BC", upsertTapScheduleOut())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out"))
            .withRequestBody(
              matchingJsonPath("eventSubType", equalTo("C5")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubUpsertTapScheduleOut(status = INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.upsertTapScheduleOut("A1234BC", upsertTapScheduleOut())
        }
      }
    }
  }

  @Nested
  inner class TapMovementOutTest {

    @Nested
    inner class CreateTapMovementOut {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateTapMovementOut()

        apiService.createTapMovementOut("A1234BC", createTapMovementOut())

        mockServer.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateTapMovementOut()

        apiService.createTapMovementOut("A1234BC", createTapMovementOut())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/out"))
            .withRequestBody(
              matchingJsonPath("movementReason", equalTo("C5")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubCreateTapMovementOut(status = INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.createTapMovementOut("A1234BC", createTapMovementOut())
        }
      }
    }
  }

  @Nested
  inner class TapMovementInTest {

    @Nested
    inner class CreateTapMovementIn {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateTapMovementIn()

        apiService.createTapMovementIn("A1234BC", createTapMovementIn())

        mockServer.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateTapMovementIn()

        apiService.createTapMovementIn("A1234BC", createTapMovementIn())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/in"))
            .withRequestBody(
              matchingJsonPath("movementReason", equalTo("C5")),
            ),
        )
      }

      @Test
      fun `will throw if error`() = runTest {
        mockServer.stubCreateTapMovementIn(status = INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          apiService.createTapMovementIn("A1234BC", createTapMovementIn())
        }
      }
    }
  }

  @Nested
  inner class OffenderTapCounts {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetTapCounts()

      apiService.getTapCounts("A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      mockServer.stubGetTapCounts()

      apiService.getTapCounts("A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/summary")),
      )
    }

    @Test
    fun `will throw if error`() = runTest {
      mockServer.stubGetTapCounts(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapCounts("A1234BC")
      }
    }
  }

  @Nested
  inner class OffenderTapIdsTest {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetTapIds()

      apiService.getTapIds("A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      mockServer.stubGetTapIds()

      apiService.getTapIds("A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/ids")),
      )
    }

    @Test
    fun `will parse response`() = runTest {
      mockServer.stubGetTapIds()

      apiService.getTapIds("A1234BC")
        .also {
          assertThat(it.applicationIds[0]).isEqualTo(1111)
          assertThat(it.scheduleOutIds[0]).isEqualTo(2222)
          assertThat(it.scheduledMovementOutIds[0].bookingId).isEqualTo(12345)
          assertThat(it.scheduledMovementOutIds[0].sequence).isEqualTo(3)
          assertThat(it.scheduledMovementInIds[0].sequence).isEqualTo(4)
          assertThat(it.unscheduledMovementOutIds[0].sequence).isEqualTo(5)
          assertThat(it.unscheduledMovementInIds[0].sequence).isEqualTo(6)
        }

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/ids")),
      )
    }

    @Test
    fun `will throw if error`() = runTest {
      mockServer.stubGetTapIds(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapIds("A1234BC")
      }
    }
  }

  @Nested
  inner class GetBookingTaps {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetBookingTaps(bookingId = 12345)

      apiService.getAllBookingTaps(bookingId = 12345)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      mockServer.stubGetBookingTaps(bookingId = 12345)

      apiService.getAllBookingTaps(bookingId = 12345)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/booking/12345/taps")),
      )
    }

    @Test
    fun `will return temporary absences`() = runTest {
      mockServer.stubGetBookingTaps(bookingId = 12345)

      val result = apiService.getAllBookingTaps(bookingId = 12345)!!

      assertThat(result.bookingId).isEqualTo(12345L)
      assertThat(result.tapApplications).hasSize(1)
      assertThat(result.tapApplications[0].taps[0].tapScheduleOut?.eventId).isEqualTo(1)
      assertThat(result.tapApplications[0].taps[0].tapScheduleIn?.eventId).isEqualTo(2)
      assertThat(result.tapApplications[0].taps[0].tapMovementOut?.sequence).isEqualTo(3)
      assertThat(result.tapApplications[0].taps[0].tapMovementIn?.sequence).isEqualTo(4)
      assertThat(result.unscheduledTapMovementOuts[0].sequence).isEqualTo(1)
      assertThat(result.unscheduledTapMovementIns[0].sequence).isEqualTo(2)
    }

    @Test
    fun `will return null when offender does not exist`() = runTest {
      mockServer.stubGetBookingTaps(bookingId = 12345, status = NOT_FOUND)

      assertThat(apiService.getAllBookingTaps(bookingId = 12345)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      mockServer.stubGetBookingTaps(bookingId = 12345, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getAllBookingTaps(bookingId = 12345)
      }
    }
  }

  @Nested
  inner class GetOffenderTaps {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetOffenderTaps(offenderNo = "A1234BC")

      apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      mockServer.stubGetOffenderTaps(offenderNo = "A1234BC")

      apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps")),
      )
    }

    @Test
    fun `will return temporary absences`() = runTest {
      mockServer.stubGetOffenderTaps(offenderNo = "A1234BC")

      val result = apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")!!

      assertThat(result.bookings).hasSize(1)
      assertThat(result.bookings[0].bookingId).isEqualTo(12345)
      assertThat(result.bookings[0].tapApplications).hasSize(1)
      assertThat(result.bookings[0].tapApplications[0].taps[0].tapScheduleOut?.eventId).isEqualTo(1)
      assertThat(result.bookings[0].tapApplications[0].taps[0].tapScheduleIn?.eventId).isEqualTo(2)
      assertThat(result.bookings[0].tapApplications[0].taps[0].tapMovementOut?.sequence).isEqualTo(3)
      assertThat(result.bookings[0].tapApplications[0].taps[0].tapMovementIn?.sequence).isEqualTo(4)
      assertThat(result.bookings[0].unscheduledTapMovementOuts[0].sequence).isEqualTo(1)
      assertThat(result.bookings[0].unscheduledTapMovementIns[0].sequence).isEqualTo(2)
    }

    @Test
    fun `will return null when offender does not exist`() = runTest {
      mockServer.stubGetOffenderTaps(NOT_FOUND)

      assertThat(apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      mockServer.stubGetOffenderTaps(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")
      }
    }
  }

  @Nested
  inner class DeleteTapScheduleOut {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteTapScheduleOut(offenderNo = "A1234BC", eventId = 4321L)

      apiService.deleteTapScheduleOut(offenderNo = "A1234BC", eventId = 4321L)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass parameters to service`() = runTest {
      mockServer.stubDeleteTapScheduleOut(offenderNo = "A1234BC", eventId = 4321L)

      apiService.deleteTapScheduleOut(offenderNo = "A1234BC", eventId = 4321L)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out/4321")),
      )
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      mockServer.stubDeleteTapScheduleOut(offenderNo = "A1234BC", eventId = 4321L, status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.deleteTapScheduleOut(offenderNo = "A1234BC", eventId = 4321L)
      }
    }
  }
}
