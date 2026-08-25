package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiExtension.Companion.transferSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  TransferSchedulerDpsApiService::class,
  TransferSchedulerDpsApiMockServer::class,
  TransferSchedulerConfiguration::class,
  RetryApiService::class,
)
class TransferSchedulerDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: TransferSchedulerDpsApiService

  @Nested
  inner class GetTransferSchedulerReconciliationDetail {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      val personIdentifier = "A1234BC"
      transferSchedulerDpsApiServer.stubGetTransferSchedulerReconciliation(personIdentifier)

      apiService.getTransferSchedulerReconciliation(personIdentifier)

      transferSchedulerDpsApiServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call get endpoint`() = runTest {
      val personIdentifier = "A1234BC"
      transferSchedulerDpsApiServer.stubGetTransferSchedulerReconciliation(personIdentifier)

      apiService.getTransferSchedulerReconciliation(personIdentifier)

      transferSchedulerDpsApiServer.verify(
        getRequestedFor(urlPathEqualTo("/reconciliation/transfers/$personIdentifier")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      val personIdentifier = "A1234BC"
      transferSchedulerDpsApiServer.stubGetTransferSchedulerReconciliation(personIdentifier)

      with(apiService.getTransferSchedulerReconciliation(personIdentifier)) {
        assertThat(transfers.size).isEqualTo(1)
        assertThat(transfers[0].transfer.schedule?.agyLocId).isEqualTo("BXI")
        assertThat(transfers[0].transfer.waitlist?.waitListStatus).isEqualTo("CANC")
        assertThat(transfers[0].movement?.fromAgyLocId).isEqualTo("BXI")
        assertThat(unscheduledMovements.size).isEqualTo(1)
        assertThat(unscheduledMovements[0].fromAgyLocId).isEqualTo("BXI")
      }
    }

    @Test
    fun `will throw if error`() = runTest {
      val personIdentifier = "A1234BC"
      transferSchedulerDpsApiServer.stubGetTransferSchedulerReconciliation(personIdentifier, status = 500)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTransferSchedulerReconciliation(personIdentifier)
      }
    }
  }
}
