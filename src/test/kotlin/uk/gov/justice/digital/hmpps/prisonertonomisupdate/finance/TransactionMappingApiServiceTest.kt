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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(TransactionMappingApiService::class, TransactionMappingApiMockServer::class, RetryApiService::class)
class TransactionMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: TransactionMappingApiService

  @Autowired
  private lateinit var mockServer: TransactionMappingApiMockServer

  @Nested
  inner class GetByNomisTransactionId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisTransactionIdOrNull(nomisTransactionId = 1234567)

      apiService.getByNomisTransactionIdOrNull(nomisTransactionId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisTransactionIdOrNull(nomisTransactionId = 1234567)

      apiService.getByNomisTransactionIdOrNull(nomisTransactionId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/transactions/nomis-transaction-id/1234567")),
      )
    }
  }
}
