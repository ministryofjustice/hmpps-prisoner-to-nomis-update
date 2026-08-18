package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.ReligionResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class ReligionMappingApiService(
  @Qualifier("mappingWebClient") webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val religionResourceApi = ReligionResourceApi(webClient)

  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "TransactionNomisApiService"),
  )

  suspend fun getByCprIds(crpReligionIds: List<String>): List<ReligionMappingDto> = religionResourceApi.getReligionMappingsByCprIds(crpReligionIds).awaitBodyWithRetry(retrySpec = backoffSpec)
}
