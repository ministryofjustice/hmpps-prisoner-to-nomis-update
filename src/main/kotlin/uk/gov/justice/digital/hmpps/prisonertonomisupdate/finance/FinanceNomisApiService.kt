package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PrisonBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PrisonerBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RootOffenderIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference

@Service
class FinanceNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "FinanceNomisApiService"),
  )

  private val prisonerApi = PrisonerBalanceResourceApi(webClient)
  private val prisonApi = PrisonBalanceResourceApi(webClient)

  suspend fun getPrisonerBalanceIdentifiersFromId(rootOffenderId: Long?, pageSize: Int?): RootOffenderIdsWithLast = prisonerApi
    .getPrisonerBalanceIdentifiersFromId(rootOffenderId, pageSize)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonerAccountDetails(rootOffenderId: Long): PrisonerBalanceDto = prisonerApi
    .getPrisonerAccountDetails(rootOffenderId)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonBalanceIds(): List<String> = prisonApi
    .getPrisonIds()
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonBalance(prisonId: String): PrisonBalanceDto = prisonApi
    .getPrisonBalance(prisonId)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getRootOffenderIds(prisonIds: List<String>?, pageNumber: Long, pageSize: Long): RestResponsePagedModel<Long> = prisonerApi
    .prepare(prisonerApi.getPrisonerBalanceIdentifiersRequestConfig(page = pageNumber.toInt(), size = pageSize.toInt(), sort = null, prisonId = prisonIds))
    .retrieve()
    .bodyToMono(typeReference<RestResponsePagedModel<Long>>())
    .retryWhen(backoffSpec)
    .awaitSingle()
}

class RestResponsePagedModel<T>(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("page") page: PageMetadata,
) : PagedModel<T>(
  PageImpl(content, PageRequest.of(page.number.toInt(), page.size.toInt()), page.totalElements),
)
