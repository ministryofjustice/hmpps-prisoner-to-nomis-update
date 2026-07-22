package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerNomisApiMockServer.Companion.upsertCourtScheduleOut
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(CourtSchedulerNomisApiService::class, CourtSchedulerNomisApiMockServer::class)
class CourtSchedulerNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtSchedulerNomisApiService

  @Autowired
  private lateinit var courtSchedulerNomisApiMockServer: CourtSchedulerNomisApiMockServer

  private val now = LocalDateTime.now()
  private val yesterday = now.minusDays(1)

  @Nested
  inner class GetOffenderCourtMovementsTest {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetOffenderCourtMovements(offenderNo = "A1234BC")

      apiService.getOffenderCourtMovementsOrNull(offenderNo = "A1234BC")

      courtSchedulerNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetOffenderCourtMovements(offenderNo = "A1234BC")

      apiService.getOffenderCourtMovementsOrNull(offenderNo = "A1234BC")

      courtSchedulerNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/court")),
      )
    }

    @Test
    fun `will return offender court movements`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetOffenderCourtMovements(offenderNo = "A1234BC")

      val result = apiService.getOffenderCourtMovementsOrNull(offenderNo = "A1234BC")!!

      assertThat(result.bookings).hasSize(1)
      assertThat(result.bookings[0].bookingId).isEqualTo(12345)
      assertThat(result.bookings[0].courtSchedules).hasSize(1)
      assertThat(result.bookings[0].courtSchedules[0].courtMovementOut?.sequence).isEqualTo(3)
      assertThat(result.bookings[0].courtSchedules[0].courtMovementIn?.sequence).isEqualTo(4)
      assertThat(result.bookings[0].unscheduledCourtMovementOuts[0].sequence).isEqualTo(1)
      assertThat(result.bookings[0].unscheduledCourtMovementIns[0].sequence).isEqualTo(2)
    }

    @Test
    fun `will return null when offender does not exist`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetOffenderCourtMovements(status = NOT_FOUND)

      apiService.getOffenderCourtMovementsOrNull(offenderNo = "A1234BC")

      assertThat(apiService.getOffenderCourtMovementsOrNull(offenderNo = "A1234BC")).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetOffenderCourtMovements(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOffenderCourtMovementsOrNull(offenderNo = "A1234BC")
      }
    }
  }

  @Nested
  inner class UpsertCourtScheduleOut {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubUpsertCourtScheduleOut("A1234BC")

      apiService.upsertCourtScheduleOut("A1234BC", upsertCourtScheduleOut())

      courtSchedulerNomisApiMockServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call upsert endpoint`() = runTest {
      courtSchedulerNomisApiMockServer.stubUpsertCourtScheduleOut("A1234BC")

      apiService.upsertCourtScheduleOut("A1234BC", upsertCourtScheduleOut())

      courtSchedulerNomisApiMockServer.verify(
        putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out?recreate=false"))
          .withRequestBody(
            matchingJsonPath("court", equalTo("LEEDMC")),
          ),
      )
    }

    @Test
    fun `will allow recreate=true`() = runTest {
      courtSchedulerNomisApiMockServer.stubUpsertCourtScheduleOut("A1234BC")

      apiService.upsertCourtScheduleOut("A1234BC", upsertCourtScheduleOut(), recreate = true)

      courtSchedulerNomisApiMockServer.verify(
        putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out?recreate=true")),
      )
    }

    @Test
    fun `will throw if error`() = runTest {
      courtSchedulerNomisApiMockServer.stubUpsertCourtScheduleOut(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.upsertCourtScheduleOut("A1234BC", upsertCourtScheduleOut())
      }
    }
  }

  @Nested
  inner class DeleteCourtScheduleOut {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubDeleteCourtScheduleOut("A1234BC", 12345L)

      apiService.deleteCourtScheduleOut("A1234BC", 12345L)

      courtSchedulerNomisApiMockServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call delete endpoint`() = runTest {
      courtSchedulerNomisApiMockServer.stubDeleteCourtScheduleOut("A1234BC", 12345L)

      apiService.deleteCourtScheduleOut("A1234BC", 12345L)

      courtSchedulerNomisApiMockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/movements/A1234BC/court/schedule/out/12345")),
      )
    }

    @Test
    fun `will throw if error`() = runTest {
      courtSchedulerNomisApiMockServer.stubDeleteCourtScheduleOut("A1234BC", 12345L, INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteCourtScheduleOut("A1234BC", 12345L)
      }
    }
  }
}
