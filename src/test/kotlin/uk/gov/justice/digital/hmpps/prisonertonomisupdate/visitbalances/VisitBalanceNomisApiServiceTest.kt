package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(
  VisitBalanceNomisApiService::class,
  VisitBalanceNomisApiMockServer::class,
)
class VisitBalanceNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceNomisApiService

  @Autowired
  private lateinit var mockServer: VisitBalanceNomisApiMockServer

  @Nested
  inner class GetVisitBalance {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitBalance()

      apiService.getVisitBalance(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitBalance()

      apiService.getVisitBalance(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234BC/visit-orders/balance")),
      )
    }

    @Test
    fun `will return visit balance`() = runTest {
      mockServer.stubGetVisitBalance()

      val visitBalance = apiService.getVisitBalance(prisonNumber = "A1234BC")!!

      assertThat(visitBalance.prisonNumber).isEqualTo("A1234BC")
      assertThat(visitBalance.remainingVisitOrders).isEqualTo(24)
      assertThat(visitBalance.remainingPrivilegedVisitOrders).isEqualTo(3)
      assertThat(visitBalance.lastIEPAllocationDate).isEqualTo("2025-01-15")
    }
  }
}
