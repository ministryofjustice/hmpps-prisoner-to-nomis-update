package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagedModelLong
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(FinanceNomisApiService::class, FinanceNomisApiMockServer::class, RetryApiService::class)
class FinanceNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: FinanceNomisApiService

  @Autowired
  private lateinit var mockServer: FinanceNomisApiMockServer

  @Nested
  inner class GetPrisonersIds {

    val fromDate = "2025-08-10"

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonersIds(response = PagedModelLong())

      apiService.getPrisonerIds()

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      mockServer.stubGetPrisonersIds(response = PagedModelLong())

      apiService.getPrisonerIds()

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prisoners/ids")),
      )
    }

    @Test
    fun `will return a long`() = runTest {
      mockServer.stubGetPrisonersIds(response = PagedModelLong(content = listOf(35L)))

      assertThat(apiService.getPrisonerIds()).isEqualTo(PagedModelLong(content = listOf(35L)))
    }
  }

  @Nested
  inner class GetPrisonerAccountDetails {

    private val sampleDto = PrisonerBalanceDto(
      rootOffenderId = 35L,
      prisonNumber = "A1234AA",
      accounts = listOf(
        PrisonerAccountDto(
          prisonId = "MDI",
          lastTransactionId = 12345L,
          transactionDate = LocalDateTime.parse("2020-09-09T01:01:01"),
          accountCode = 1001,
          balance = BigDecimal.valueOf(100.00),
          holdBalance = BigDecimal.valueOf(50.00),
        ),
      ),
    )

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonerAccountDetails(35L, sampleDto)

      apiService.getPrisonerAccountDetails(35L)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      mockServer.stubGetPrisonerAccountDetails(35L, sampleDto)

      apiService.getPrisonerAccountDetails(35L)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prisoners/35/balance")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      mockServer.stubGetPrisonerAccountDetails(35L, sampleDto)

      val data = apiService.getPrisonerAccountDetails(35L)

      assertThat(data).isEqualTo(sampleDto)
    }
  }
}
