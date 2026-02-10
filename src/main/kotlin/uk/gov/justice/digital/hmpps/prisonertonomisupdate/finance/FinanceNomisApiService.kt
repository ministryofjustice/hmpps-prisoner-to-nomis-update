package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PrisonBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PrisonerBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RootOffenderIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class FinanceNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "FinanceNomisApiService"),
  )

  private val prisonBalanceApi = PrisonBalanceResourceApi(webClient)
  private val prisonerBalanceApi = PrisonerBalanceResourceApi(webClient)

  suspend fun getPrisonBalanceIds(): List<String> = prisonBalanceApi
    .getPrisonIds()
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonBalance(prisonId: String): PrisonBalanceDto = prisonBalanceApi
    .getPrisonBalance(prisonId)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonerBalanceIdentifiersFromId(
    rootOffenderId: Long?,
    pageSize: Int?,
    prisonIds: List<String>? = null,
  ): RootOffenderIdsWithLast = prisonerBalanceApi
    .getPrisonerBalanceIdentifiersFromId(rootOffenderId, pageSize, prisonIds)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonerAccountDetails(rootOffenderId: Long): PrisonerBalanceDto = prisonerBalanceApi
    .getPrisonerAccountDetails(rootOffenderId)
    .retryWhen(backoffSpec)
    .awaitSingle()
}
