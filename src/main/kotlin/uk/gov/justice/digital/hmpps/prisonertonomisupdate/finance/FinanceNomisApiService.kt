package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PrisonerBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAggregatedAccountsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class FinanceNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "FinanceNomisApiService"),
  )

  private val prisonerBalanceApi = PrisonerBalanceResourceApi(webClient)

  suspend fun getPrisonerAccountsToReconcile(rootOffenderId: Long): PrisonerAggregatedAccountsDto = prisonerBalanceApi
    .getAggregatedAccountsForId(rootOffenderId)
    .retryWhen(backoffSpec)
    .awaitSingle()
}
