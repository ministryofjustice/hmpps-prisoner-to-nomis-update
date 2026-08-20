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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AggregatedAccountDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAggregatedAccountsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal

@SpringAPIServiceTest
@Import(FinanceNomisApiService::class, PrisonerBalanceNomisApiMockServer::class, RetryApiService::class)
class PrisonerBalanceNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: FinanceNomisApiService

  @Autowired
  private lateinit var mockServer: PrisonerBalanceNomisApiMockServer

  @Nested
  @DisplayName("GET /finance/prisoners/rootOffenderId/{rootOffenderId}/balance/reconcile")
  inner class GetPrisonerAggregatedAccounts {

    private val sampleDto = PrisonerAggregatedAccountsDto(
      rootOffenderId = 35L,
      prisonNumber = "A1234AA",
      accounts = listOf(
        AggregatedAccountDto(
          accountCode = 1001,
          balance = BigDecimal.valueOf(100.00),
        ),
      ),
    )

    @Test
    fun `will pass oath2 token to service`() = runTest {
      apiService.getPrisonerAccountsToReconcile(35L)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      mockServer.stubGetPrisonerAccounts(35L, sampleDto)

      apiService.getPrisonerAccountsToReconcile(35L)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prisoners/rootOffenderId/35/balance/reconcile")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      mockServer.stubGetPrisonerAccounts(35L, sampleDto)

      val data = apiService.getPrisonerAccountsToReconcile(35L)

      assertThat(data).isEqualTo(sampleDto)
    }
  }
}
