package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceDpsApiExtension.Companion.visitBalanceDpsApi
import java.util.UUID

@SpringAPIServiceTest
@Import(
  VisitBalanceDpsApiService::class,
  VisitBalanceConfiguration::class,
  VisitBalanceDpsApiMockServer::class,
  RetryApiService::class,
)
class VisitBalanceDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceDpsApiService

  @Nested
  inner class GetVisitBalance {
    @Test
    internal fun `will pass oath2 token to visit balance endpoint`() = runTest {
      visitBalanceDpsApi.stubGetVisitBalance()

      apiService.getVisitBalance("A1234BC")

      visitBalanceDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET visit balance endpoint`() = runTest {
      visitBalanceDpsApi.stubGetVisitBalance()

      apiService.getVisitBalance("A1234BC")

      visitBalanceDpsApi.verify(
        getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/A1234BC/balance")),
      )
    }
  }

  @Nested
  inner class GetVisitBalanceAdjustment {
    private val visitBalanceAdjustmentId = UUID.randomUUID().toString()
    private val prisonNumber = "A1234BG"

    @Test
    internal fun `will pass oath2 token to visit balance adjustment endpoint`() = runTest {
      visitBalanceDpsApi.stubGetVisitBalanceAdjustment(prisonNumber, visitBalanceAdjustmentId)

      apiService.getVisitBalanceAdjustment(prisonNumber, visitBalanceAdjustmentId)

      visitBalanceDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET visit balance adjustment endpoint`() = runTest {
      visitBalanceDpsApi.stubGetVisitBalanceAdjustment(prisonNumber, visitBalanceAdjustmentId)

      apiService.getVisitBalanceAdjustment(prisonNumber, visitBalanceAdjustmentId)

      visitBalanceDpsApi.verify(
        getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$prisonNumber/adjustments/$visitBalanceAdjustmentId")),
      )
    }
  }
}
