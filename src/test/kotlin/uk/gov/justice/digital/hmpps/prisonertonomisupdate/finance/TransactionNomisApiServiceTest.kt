package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(TransactionNomisApiService::class, TransactionNomisApiMockServer::class, RetryApiService::class)
class TransactionNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: TransactionNomisApiService

  @Autowired
  private lateinit var mockServer: TransactionNomisApiMockServer

  @Nested
  @DisplayName("GET /transactions/{date}/first")
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
  @DisplayName("GET /transactions/from/{transactionId}/{transactionEntrySequence}?pageSize={pageSize")
  inner class GetPrisonerTransactions {

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

  @Nested
  @DisplayName("GET /transactions/{transactionId}/general-ledger")
  inner class GetPrisonTransaction {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonTransaction()

      apiService.getPrisonTransaction(1234)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetPrisonTransaction()

      apiService.getPrisonTransaction(1234)

      mockServer.verify(
        getRequestedFor(urlEqualTo("/transactions/1234/general-ledger")),
      )
    }

    @Test
    fun `will return the prison (GL) transactions`() = runTest {
      mockServer.stubGetPrisonTransaction(
        response = listOf(
          nomisPrisonTransaction(),
          nomisPrisonTransaction().copy(generalLedgerEntrySequence = 2, accountCode = 2101),
        ),
      )

      val prisonTransactions = apiService.getPrisonTransaction(1234)

      assertThat(prisonTransactions!!.size).isEqualTo(2)
      with(prisonTransactions.first()) {
        assertThat(transactionId).isEqualTo(1234)
        assertThat(transactionEntrySequence).isEqualTo(1)
        assertThat(generalLedgerEntrySequence).isEqualTo(1)
        assertThat(caseloadId).isEqualTo("MDI")
        assertThat(amount).isEqualTo(BigDecimal(5.4))
        assertThat(type).isEqualTo("SPEN")
        assertThat(postingType).isEqualTo(GeneralLedgerTransactionDto.PostingType.CR)
        assertThat(accountCode).isEqualTo(1501)
        assertThat(description).isEqualTo("General Ledger Account Transfer")
        assertThat(transactionTimestamp).isEqualTo(LocalDateTime.parse("2021-02-03T04:05:09"))
        assertThat(reference).isEqualTo("ref 123")
        assertThat(createdAt).isEqualTo(LocalDateTime.parse("2021-02-03T04:05:07"))
        assertThat(createdBy).isEqualTo("J_BROWN")
        assertThat(createdByDisplayName).isEqualTo("Jim Brown")
        assertThat(lastModifiedAt).isEqualTo(LocalDateTime.parse("2021-02-03T04:05:59"))
        assertThat(lastModifiedBy).isEqualTo("T_SMITH")
        assertThat(lastModifiedByDisplayName).isEqualTo("Tim Smith")
      }
      with(prisonTransactions[1]) {
        assertThat(transactionId).isEqualTo(1234)
        assertThat(transactionEntrySequence).isEqualTo(1)
        assertThat(generalLedgerEntrySequence).isEqualTo(2)
        assertThat(accountCode).isEqualTo(2101)
      }
    }
  }

  @Nested
  @DisplayName("GET /transactions/prison/{prisonId}?entryDate={entryDate}")
  inner class GetPrisonTransactions {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonTransactionsOn(date = LocalDate.parse("2021-02-03"))

      apiService.getPrisonTransactions("MDI", entryDate = LocalDate.parse("2021-02-03"))

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetPrisonTransactionsOn(date = LocalDate.parse("2021-02-03"))

      apiService.getPrisonTransactions("MDI", entryDate = LocalDate.parse("2021-02-03"))

      mockServer.verify(
        getRequestedFor(urlEqualTo("/transactions/prison/MDI?entryDate=2021-02-03")),
      )
    }

    @Test
    fun `will return the prison (GL) transactions`() = runTest {
      mockServer.stubGetPrisonTransactionsOn(
        date = LocalDate.parse("2021-02-03"),
        response = listOf(
          nomisPrisonTransaction(),
          nomisPrisonTransaction().copy(generalLedgerEntrySequence = 2, accountCode = 2101),
        ),
      )

      val prisonTransactions = apiService.getPrisonTransactions("MDI", entryDate = LocalDate.parse("2021-02-03"))

      assertThat(prisonTransactions.size).isEqualTo(2)
      with(prisonTransactions.first()) {
        assertThat(transactionId).isEqualTo(1234)
        assertThat(transactionEntrySequence).isEqualTo(1)
        assertThat(generalLedgerEntrySequence).isEqualTo(1)
        assertThat(caseloadId).isEqualTo("MDI")
        assertThat(amount).isEqualTo(BigDecimal(5.4))
        assertThat(type).isEqualTo("SPEN")
        assertThat(postingType).isEqualTo(GeneralLedgerTransactionDto.PostingType.CR)
        assertThat(accountCode).isEqualTo(1501)
        assertThat(description).isEqualTo("General Ledger Account Transfer")
        assertThat(transactionTimestamp).isEqualTo(LocalDateTime.parse("2021-02-03T04:05:09"))
        assertThat(reference).isEqualTo("ref 123")
        assertThat(createdAt).isEqualTo(LocalDateTime.parse("2021-02-03T04:05:07"))
        assertThat(createdBy).isEqualTo("J_BROWN")
        assertThat(createdByDisplayName).isEqualTo("Jim Brown")
        assertThat(lastModifiedAt).isEqualTo(LocalDateTime.parse("2021-02-03T04:05:59"))
        assertThat(lastModifiedBy).isEqualTo("T_SMITH")
        assertThat(lastModifiedByDisplayName).isEqualTo("Tim Smith")
      }
      with(prisonTransactions[1]) {
        assertThat(transactionId).isEqualTo(1234)
        assertThat(transactionEntrySequence).isEqualTo(1)
        assertThat(generalLedgerEntrySequence).isEqualTo(2)
        assertThat(accountCode).isEqualTo(2101)
      }
    }
  }
}
