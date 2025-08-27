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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@SpringAPIServiceTest
@Import(TransactionNomisApiService::class, TransactionNomisApiMockServer::class, RetryApiService::class)
class TransactionNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: TransactionNomisApiService

  @Autowired
  private lateinit var mockServer: TransactionNomisApiMockServer

  @Nested
  inner class GetTransactionIdFromDate {

    val fromDate = "2025-08-10"

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetTransactionFromDate()

      apiService.getFirstTransactionIdFrom(LocalDate.parse(fromDate))

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      mockServer.stubGetTransactionFromDate()

      apiService.getFirstTransactionIdFrom(LocalDate.parse(fromDate))

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/transactions/$fromDate/first")),
      )
    }

    @Test
    fun `will return a long`() = runTest {
      mockServer.stubGetTransactionFromDate()

      val lastTransactionId = apiService.getFirstTransactionIdFrom(LocalDate.parse(fromDate))
      assertThat(lastTransactionId).isEqualTo(45)
    }
  }

  @Nested
  inner class GetTransactions {

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetTransactions()

      apiService.getTransactions()

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      mockServer.stubGetTransactions()

      apiService.getTransactions()

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/transactions/from/0/1")),
      )
    }

    @Test
    fun `will request just a page of transactions from specified transaction`() = runTest {
      mockServer.stubGetTransactions(lastTransactionId = 1234)

      apiService.getTransactions(lastTransactionId = 1234, pageSize = 100)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/transactions/from/1234/1"))
          .withQueryParam("pageSize", equalTo("100")),
      )
    }

    @Test
    fun `will return a list of transactions`() = runTest {
      mockServer.stubGetTransactions(lastTransactionId = 1234)

      val transactions = apiService.getTransactions(lastTransactionId = 1234, pageSize = 100)

      assertThat(transactions.size).isEqualTo(1)
      assertThat(transactions[0].transactionId).isEqualTo(1001)
    }
  }
}
