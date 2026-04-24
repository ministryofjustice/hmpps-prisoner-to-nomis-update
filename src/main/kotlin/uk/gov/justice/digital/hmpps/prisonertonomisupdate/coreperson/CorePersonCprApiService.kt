package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.api.PrisonApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.DpsPrisonRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CorePersonCprApiService(
  corePersonApiWebClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", this::class.java.simpleName),
  )

  private val personApi = PrisonApi(corePersonApiWebClient)

  suspend fun getCorePerson(prisonNumber: String): DpsPrisonRecord? = personApi
    .prepare(personApi.getByPrisonNumberDpsRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullForNotFound(backoffSpec)
}
