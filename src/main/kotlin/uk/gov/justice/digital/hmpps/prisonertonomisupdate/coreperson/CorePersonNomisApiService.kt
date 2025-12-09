package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.CorePersonResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CorePersonNomisApiService(
  @Qualifier("nomisApiWebClient") webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", this::class.java.simpleName),
  )

  private val api = CorePersonResourceApi(webClient)

  suspend fun getPrisoner(prisonNumber: String): CorePerson? = api
    .prepare(api.getOffenderRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullForNotFound(retrySpec = backoffSpec)
}
