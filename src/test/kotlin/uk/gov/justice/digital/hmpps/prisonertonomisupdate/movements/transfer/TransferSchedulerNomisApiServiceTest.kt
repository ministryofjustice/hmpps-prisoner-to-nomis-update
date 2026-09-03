package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerNomisApiMockServer.Companion.upsertTransferScheduleOut

@SpringAPIServiceTest
@Import(TransferSchedulerNomisApiService::class, TransferSchedulerNomisApiMockServer::class)
class TransferSchedulerNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: TransferSchedulerNomisApiService

  @Autowired
  private lateinit var transferSchedulerNomisApiMockServer: TransferSchedulerNomisApiMockServer

  @Nested
  inner class GetOffenderTransferMovementsTest {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      transferSchedulerNomisApiMockServer.stubGetOffenderTransferMovements(offenderNo = "A1234BC")

      apiService.getOffenderTransferMovementsOrNull(offenderNo = "A1234BC")

      transferSchedulerNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      transferSchedulerNomisApiMockServer.stubGetOffenderTransferMovements(offenderNo = "A1234BC")

      apiService.getOffenderTransferMovementsOrNull(offenderNo = "A1234BC")

      transferSchedulerNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/transfer")),
      )
    }

    @Test
    fun `will return offender transfer movements`() = runTest {
      transferSchedulerNomisApiMockServer.stubGetOffenderTransferMovements(offenderNo = "A1234BC")

      val result = apiService.getOffenderTransferMovementsOrNull(offenderNo = "A1234BC")!!

      assertThat(result.bookings).hasSize(1)
      assertThat(result.bookings[0].bookingId).isEqualTo(12345)
      assertThat(result.bookings[0].transferSchedules).hasSize(1)
      assertThat(result.bookings[0].transferSchedules[0].movement?.sequence).isEqualTo(3)
      assertThat(result.bookings[0].unscheduledTransferMovements[0].sequence).isEqualTo(4)
    }

    @Test
    fun `will return null when offender does not exist`() = runTest {
      transferSchedulerNomisApiMockServer.stubGetOffenderTransferMovements(status = NOT_FOUND)

      assertThat(apiService.getOffenderTransferMovementsOrNull(offenderNo = "A1234BC")).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      transferSchedulerNomisApiMockServer.stubGetOffenderTransferMovements(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOffenderTransferMovementsOrNull(offenderNo = "A1234BC")
      }
    }
  }

  @Nested
  inner class UpsertTransferSchedule {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      transferSchedulerNomisApiMockServer.stubUpsertTransferScheduleOut("A1234BC")

      apiService.upsertTransferSchedule("A1234BC", upsertTransferScheduleOut())

      transferSchedulerNomisApiMockServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call upsert endpoint`() = runTest {
      transferSchedulerNomisApiMockServer.stubUpsertTransferScheduleOut("A1234BC")

      apiService.upsertTransferSchedule("A1234BC", upsertTransferScheduleOut())

      transferSchedulerNomisApiMockServer.verify(
        putRequestedFor(urlEqualTo("/movements/A1234BC/transfers/schedule/out"))
          .withRequestBody(
            matchingJsonPath("fromPrison", equalTo("BXI")),
          ),
      )
    }

    @Test
    fun `will throw if error`() = runTest {
      transferSchedulerNomisApiMockServer.stubUpsertTransferScheduleOut(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.upsertTransferSchedule("A1234BC", upsertTransferScheduleOut())
      }
    }
  }
}
