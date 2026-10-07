package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.CorePersonMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.ReligionResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CorePersonMappingApiService(
  @Qualifier("mappingWebClient") webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val corePersonApi = CorePersonMappingResourceApi(webClient)
  private val religionApi = ReligionResourceApi(webClient)

  private val corePersonBackoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CorePersonMappingApiService"),
  )
  private val religionBackoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CorePersonMappingApiService-religion"),
  )

  suspend fun getByCprContactIdOrNull(cprContactId: String): CorePersonContactMappingDto? = corePersonApi
    .prepare(corePersonApi.getCorePersonContactMappingByCprIdRequestConfig(cprContactId))
    .retrieve()
    .awaitBodyOrNullForNotFound(corePersonBackoffSpec)

  suspend fun getByCprContactId(cprContactId: String): CorePersonContactMappingDto = corePersonApi
    .getCorePersonContactMappingByCprId(cprContactId)
    .awaitBodyWithRetry(corePersonBackoffSpec)

  suspend fun createContactMapping(mapping: CorePersonContactMappingDto) = corePersonApi
    .prepare(corePersonApi.createCorePersonContactMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getByCprAddressIdOrNull(cprAddressId: String): CorePersonAddressMappingDto? = corePersonApi
    .getAddressMappingByCprId(cprAddressId)
    .awaitBodyOrNullForNotFound()

  suspend fun getByCprIds(cprReligionIds: List<String>): List<ReligionMappingDto> = religionApi
    .getReligionMappingsByCprIds(cprReligionIds)
    .awaitBodyWithRetry(retrySpec = religionBackoffSpec)

  suspend fun getReligionByCprIdOrNull(cprReligionId: String): ReligionMappingDto? = religionApi
    .prepare(religionApi.getReligionMappingByCprIdRequestConfig(cprReligionId))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createReligionMapping(mapping: ReligionMappingDto) = religionApi
    .prepare(religionApi.createReligionMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()
}
