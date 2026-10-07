package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.CorePersonMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CorePersonContactMappingApiService(
  @Qualifier("mappingWebClient") webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = CorePersonMappingResourceApi(webClient)

  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", this::class.java.simpleName),
  )

  suspend fun getByCprContactIdOrNull(cprContactId: String): CorePersonContactMappingDto? = api
    .prepare(api.getCorePersonContactMappingByCprIdRequestConfig(cprContactId))
    .retrieve()
    .awaitBodyOrNullForNotFound(backoffSpec)

  suspend fun getByCprContactId(cprContactId: String): CorePersonContactMappingDto = api
    .getCorePersonContactMappingByCprId(cprContactId)
    .awaitBodyWithRetry(backoffSpec)

  suspend fun createContactMapping(mapping: CorePersonContactMappingDto) = api
    .prepare(api.createCorePersonContactMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()
}
