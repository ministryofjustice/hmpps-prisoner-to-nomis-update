package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath

@SpringAPIServiceTest
@Import(
  VisitBalanceNomisApiService::class,
  VisitBalanceNomisApiMockServer::class,
  RetryApiService::class,
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
      mockServer.stubGetVisitBalance("A1234BC")

      apiService.getVisitBalance(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitBalance("A1234BC")

      apiService.getVisitBalance(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234BC/visit-balance")),
      )
    }

    @Test
    fun `will return visit balance`() = runTest {
      mockServer.stubGetVisitBalance("A1234BC")

      val visitBalance = apiService.getVisitBalance(prisonNumber = "A1234BC")!!

      assertThat(visitBalance.remainingVisitOrders).isEqualTo(24)
      assertThat(visitBalance.remainingPrivilegedVisitOrders).isEqualTo(3)
    }
  }

  @Nested
  inner class CreateVisitBalanceAdjustment {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubPostVisitBalanceAdjustment(prisonNumber = "A1234BC")

      apiService.createVisitBalanceAdjustment(prisonNumber = "A1234BC", visitBalanceAdjustment = createVisitBalanceAdjRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass balance adjustment data to Nomis`() = runTest {
      mockServer.stubPostVisitBalanceAdjustment(prisonNumber = "A1234BC")

      apiService.createVisitBalanceAdjustment(prisonNumber = "A1234BC", visitBalanceAdjustment = createVisitBalanceAdjRequest())

      mockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("previousVisitOrderCount", equalTo("12"))
          .withRequestBodyJsonPath("visitOrderChange", equalTo("2"))
          .withRequestBodyJsonPath("previousPrivilegedVisitOrderCount", equalTo("7"))
          .withRequestBodyJsonPath("privilegedVisitOrderChange", equalTo("-1"))
          .withRequestBodyJsonPath("adjustmentDate", equalTo("2021-01-18"))
          .withRequestBodyJsonPath("comment", equalTo("A comment")),
      )
    }

    @Test
    fun `will call the adjustment endpoint`() = runTest {
      mockServer.stubPostVisitBalanceAdjustment(prisonNumber = "A1234BC")

      apiService.createVisitBalanceAdjustment(prisonNumber = "A1234BC", visitBalanceAdjustment = createVisitBalanceAdjRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/prisoners/A1234BC/visit-balance-adjustments")),
      )
    }

    @Test
    fun `will return visit balance adjustment id`() = runTest {
      mockServer.stubPostVisitBalanceAdjustment(prisonNumber = "A1234BC")

      val visitBalanceAdjustment = apiService.createVisitBalanceAdjustment(prisonNumber = "A1234BC", visitBalanceAdjustment = createVisitBalanceAdjRequest())

      assertThat(visitBalanceAdjustment.visitBalanceAdjustmentId).isEqualTo(1234)
    }
  }

  @Nested
  inner class UpdateVisitBalance {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubPutVisitBalance(prisonNumber = "A1234BC")

      apiService.updateVisitBalance(prisonNumber = "A1234BC", visitBalance = updateVisitBalanceRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass balance data to Nomis`() = runTest {
      mockServer.stubPutVisitBalance(prisonNumber = "A1234BC")

      apiService.updateVisitBalance(prisonNumber = "A1234BC", visitBalance = updateVisitBalanceRequest())

      mockServer.verify(
        putRequestedFor(anyUrl())
          .withRequestBodyJsonPath("remainingVisitOrders", equalTo("24"))
          .withRequestBodyJsonPath("remainingPrivilegedVisitOrders", equalTo("3")),
      )
    }

    @Test
    fun `will call the update balance endpoint`() = runTest {
      mockServer.stubPutVisitBalance(prisonNumber = "A1234BC")

      apiService.updateVisitBalance(prisonNumber = "A1234BC", visitBalance = updateVisitBalanceRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/visit-balance")),
      )
    }
  }
}
