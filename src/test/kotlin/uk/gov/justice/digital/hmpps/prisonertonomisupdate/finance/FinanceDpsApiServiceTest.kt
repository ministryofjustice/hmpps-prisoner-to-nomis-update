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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.dpsFinanceServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerSubAccountDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal
import java.util.UUID

@SpringAPIServiceTest
@Import(
  FinanceDpsApiService::class,
  FinanceConfiguration::class,
  FinanceDpsApiMockServer::class,
  RetryApiService::class,
)
class FinanceDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: FinanceDpsApiService

  @Nested
  inner class GetPrisonerTransaction {
    val transactionId: UUID = UUID.randomUUID()

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonerTransaction(transactionId.toString())

      apiService.getPrisonerTransactionOrNull(transactionId)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonerTransaction(transactionId.toString())

      apiService.getPrisonerTransactionOrNull(transactionId)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/offender-transactions/$transactionId")),
      )
    }
  }

  @Nested
  inner class GetPrisonTransaction {
    val transactionId: UUID = UUID.randomUUID()

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonTransaction(transactionId.toString())

      apiService.getPrisonTransaction(transactionId)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonTransaction(transactionId.toString())

      apiService.getPrisonTransaction(transactionId)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/general-ledger-transactions/$transactionId")),
      )
    }
  }

  @Nested
  inner class ListPrisonerAccounts {
    val prisonerNo = "A1234AA"
    val sampleResponse = PrisonerEstablishmentBalanceDetailsList(items = emptyList())

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsFinanceServer.stubListPrisonerAccounts(prisonerNo, sampleResponse)

      apiService.getPrisonerAccounts(prisonerNo)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubListPrisonerAccounts(prisonerNo, sampleResponse)

      apiService.getPrisonerAccounts(prisonerNo)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/prisoner-balances/$prisonerNo")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      dpsFinanceServer.stubListPrisonerAccounts(prisonerNo, sampleResponse)

      val data = apiService.getPrisonerAccounts(prisonerNo)

      assertThat(data).isEqualTo(sampleResponse)
    }
  }

  // TODO Check if we need this
  @Nested
  inner class GetPrisonerSubAccountDetails {
    val prisonerNo = "A1234AA"
    val sampleResponse = PrisonerSubAccountDetails(
      code = 1001,
      name = "name",
      prisonNumber = prisonerNo,
      balance = BigDecimal.ONE,
      holdBalance = BigDecimal.TWO,
    )

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonerSubAccountDetails(prisonerNo, 1001, sampleResponse)

      apiService.getPrisonerSubAccountDetails(prisonerNo, 1001)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonerSubAccountDetails(prisonerNo, 1001, sampleResponse)

      apiService.getPrisonerSubAccountDetails(prisonerNo, 1001)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/$prisonerNo/accounts/1001")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      dpsFinanceServer.stubGetPrisonerSubAccountDetails(prisonerNo, 1001, sampleResponse)

      val data = apiService.getPrisonerSubAccountDetails(prisonerNo, 1001)

      assertThat(data).isEqualTo(sampleResponse)
    }
  }

  @Nested
  @DisplayName("/reconcile/general-ledger-balances/{prisonId}")
  inner class ListPrisonAccounts {
    val prisonId = "LEI"
    val sampleResponse = GeneralLedgerBalanceDetailsList(items = emptyList())

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonBalance(prisonId, sampleResponse)

      apiService.getPrisonAccounts(prisonId)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonBalance(prisonId, sampleResponse)

      apiService.getPrisonAccounts(prisonId)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/general-ledger-balances/$prisonId")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      dpsFinanceServer.stubGetPrisonBalance(prisonId, sampleResponse)

      val data = apiService.getPrisonAccounts(prisonId)

      assertThat(data).isEqualTo(sampleResponse)
    }
  }
}
