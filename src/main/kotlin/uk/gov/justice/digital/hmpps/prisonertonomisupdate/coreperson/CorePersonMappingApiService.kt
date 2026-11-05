package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.CorePersonAddressMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.CorePersonContactMappingResourceApi
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
  private val contactApi = CorePersonContactMappingResourceApi(webClient)
  private val addressApi = CorePersonAddressMappingResourceApi(webClient)
  private val religionApi = ReligionResourceApi(webClient)

  private val corePersonBackoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CorePersonMappingApiService"),
  )
  private val religionBackoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CorePersonMappingApiService-religion"),
  )

  suspend fun getByCprContactIdOrNull(cprContactId: String): CorePersonContactMappingDto? = contactApi
    .prepare(contactApi.getCorePersonContactMappingByCprIdRequestConfig(cprContactId))
    .retrieve()
    .awaitBodyOrNullForNotFound(corePersonBackoffSpec)

  suspend fun getByCprContactId(cprContactId: String): CorePersonContactMappingDto = contactApi
    .getCorePersonContactMappingByCprId(cprContactId)
    .awaitBodyWithRetry(corePersonBackoffSpec)

  suspend fun createContactMapping(mapping: CorePersonContactMappingDto) = contactApi
    .prepare(contactApi.createCorePersonContactMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getByCprAddressIdOrNull(cprAddressId: String): CorePersonAddressMappingDto? = addressApi
    .getAddressMappingByCprId(cprAddressId)
    .awaitBodyOrNullForNotFound()

  suspend fun createAddressMapping(mapping: CorePersonAddressMappingDto) = addressApi
    .prepare(addressApi.createCorePersonAddressMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun updatePrisonNumberOnReligionMappingsByCprIds(prisonNumber: String, cprReligionIds: List<String>): List<ReligionMappingDto> = religionApi
    .updatePrisonNumberOnReligionMappingsByCprIds(prisonNumber, cprReligionIds)
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
