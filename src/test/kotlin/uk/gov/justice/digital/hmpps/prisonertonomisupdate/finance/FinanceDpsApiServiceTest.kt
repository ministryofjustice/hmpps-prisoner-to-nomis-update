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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.dpsFinanceServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SubAccountBalanceForReconciliation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
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
    val nomisTransactionId = 1234L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonerTransaction(nomisTransactionId = nomisTransactionId)

      apiService.getPrisonerTransactionOrNull(nomisTransactionId)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonerTransaction(nomisTransactionId = nomisTransactionId)

      apiService.getPrisonerTransactionOrNull(nomisTransactionId)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/offender-transactions/$nomisTransactionId")),
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
  inner class ReconcilePrisonerAccounts {
    val prisonerNo = "A1234AA"
    val sampleResponse = emptyMap<String, SubAccountBalanceForReconciliation>()

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonerAccounts(prisonerNo, sampleResponse)

      apiService.getPrisonerAccounts(prisonerNo)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubGetPrisonerAccounts(prisonerNo, sampleResponse)

      apiService.getPrisonerAccounts(prisonerNo)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/reconcile/prisoner-balances/$prisonerNo")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      dpsFinanceServer.stubGetPrisonerAccounts(prisonerNo, sampleResponse)

      val data = apiService.getPrisonerAccounts(prisonerNo)

      assertThat(data).isEqualTo(sampleResponse)
    }
  }

  @Nested
  inner class ReconcilePrisonAccounts {
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
