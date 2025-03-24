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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceDpsApiExtension.Companion.dpsVisitBalanceServer

@SpringAPIServiceTest
@Import(
  VisitBalanceDpsApiService::class,
  VisitBalanceConfiguration::class,
  VisitBalanceDpsApiMockServer::class,
)
class VisitBalanceDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceDpsApiService

  @Nested
  inner class GeVisitBalance {
    @Test
    internal fun `will pass oath2 token to organisation endpoint`() = runTest {
      dpsVisitBalanceServer.stubGetVisitBalance()

      apiService.getVisitBalance("A1234BC")

      dpsVisitBalanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsVisitBalanceServer.stubGetVisitBalance()

      apiService.getVisitBalance("A1234BC")

      dpsVisitBalanceServer.verify(
        getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/A1234BC/balance")),
      )
    }
  }
}
