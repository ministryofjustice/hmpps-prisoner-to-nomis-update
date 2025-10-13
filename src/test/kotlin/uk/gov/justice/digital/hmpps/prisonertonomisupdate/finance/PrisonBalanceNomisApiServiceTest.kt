package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@SpringAPIServiceTest
@Import(FinanceNomisApiService::class, FinanceNomisApiMockServer::class, RetryApiService::class)
class PrisonBalanceNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: FinanceNomisApiService

  @Autowired
  private lateinit var mockServer: FinanceNomisApiMockServer

  @Nested
  @DisplayName("GET /finance/prison/ids")
  inner class GetPrisonBalanceIds {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonBalanceIds()

      apiService.getPrisonBalanceIds()

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetPrisonBalanceIds()

      apiService.getPrisonBalanceIds()

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prison/ids")),
      )
    }

    @Test
    fun `will return the prison balances`() = runTest {
      mockServer.stubGetPrisonBalanceIds()

      val ids = apiService.getPrisonBalanceIds()

      assertThat(ids).hasSize(20)
      assertThat(ids[0]).isEqualTo("MDI1")
      assertThat(ids[19]).isEqualTo("MDI20")
    }
  }

  @Nested
  @DisplayName("GET /finance/prison/{prisonId}/balance")
  inner class GetPrisonBalance {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonBalance(prisonId = "MDI")

      apiService.getPrisonBalance(prisonId = "MDI")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetPrisonBalance(prisonId = "MDI")

      apiService.getPrisonBalance(prisonId = "MDI")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prison/MDI/balance")),
      )
    }

    @Test
    fun `will return the prison balances`() = runTest {
      mockServer.stubGetPrisonBalance(prisonId = "MDI")

      val prisonBalance = apiService.getPrisonBalance(prisonId = "MDI")

      assertThat(prisonBalance.accountBalances).hasSize(1)
      assertThat(prisonBalance.accountBalances[0].balance).isEqualTo("23.45")
      assertThat(prisonBalance.accountBalances[0].accountCode).isEqualTo(2101)
      assertThat(prisonBalance.accountBalances[0].transactionDate.toLocalDate()).isEqualTo(LocalDate.parse("2025-06-02"))
    }
  }
}
