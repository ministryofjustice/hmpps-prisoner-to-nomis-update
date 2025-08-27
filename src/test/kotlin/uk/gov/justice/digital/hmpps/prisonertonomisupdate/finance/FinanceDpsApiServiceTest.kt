package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.dpsFinanceServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import java.util.UUID

@SpringAPIServiceTest
@Import(
  FinanceDpsApiService::class,
  FinanceConfiguration::class,
  FinanceDpsApiMockServer::class,
)
class FinanceDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: FinanceDpsApiService

  @Nested
  inner class GetSyncOffenderTransaction {
    val transactionId: UUID = UUID.randomUUID()

    @Test
    internal fun `will pass oath2 token to organisation endpoint`() = runTest {
      dpsFinanceServer.stubGetOffenderTransaction(transactionId.toString())

      apiService.getOffenderTransaction(transactionId)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubGetOffenderTransaction(transactionId.toString())

      apiService.getOffenderTransaction(transactionId)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/offender-transactions/$transactionId")),
      )
    }
  }

  @Nested
  inner class GetSyncGeneralLedgerTransaction {
    val transactionId: UUID = UUID.randomUUID()

    @Test
    internal fun `will pass oath2 token to organisation endpoint`() = runTest {
      dpsFinanceServer.stubGetGeneralLedgerTransaction(transactionId.toString())

      apiService.getGeneralLedgerTransaction(transactionId)

      dpsFinanceServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsFinanceServer.stubGetGeneralLedgerTransaction(transactionId.toString())

      apiService.getGeneralLedgerTransaction(transactionId)

      dpsFinanceServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/general-ledger-transactions/$transactionId")),
      )
    }
  }
}
