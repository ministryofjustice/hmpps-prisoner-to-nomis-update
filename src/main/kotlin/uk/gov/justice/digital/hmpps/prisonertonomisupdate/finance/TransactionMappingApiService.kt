package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class TransactionMappingApiService(
  @Qualifier("mappingWebClient") val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "TransactionMappingApiService"),
  )

  suspend fun getByNomisTransactionIdOrNull(nomisTransactionId: Long): TransactionMappingDto? = webClient.get()
    .uri("/mapping/transactions/nomis-transaction-id/{nomisTransactionId}", nomisTransactionId)
    .retrieve()
    .awaitBodyOrNullForNotFound(backoffSpec)
}
