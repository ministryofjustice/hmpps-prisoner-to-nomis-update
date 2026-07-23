package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.NOMISDPSMappingLookupApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.PropertyContainerMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.NomisDpsLocationMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class PropertyMappingService(
  @Qualifier("mappingWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = PropertyContainerMappingResourceApi(webClient)
  private val externalApi = NOMISDPSMappingLookupApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "PropertyMappingService"),
  )

  suspend fun create(propertyContainerMappingDto: PropertyContainerMappingDto) = api
    .createPropertyContainerMapping(propertyContainerMappingDto)
    .awaitSingle()

  suspend fun getMappingByNomisId(nomisId: Long) = api
    .getPropertyContainerMappingByNomisId(nomisId)
    .awaitSingle()

  suspend fun getMappingByNomisIdOrNull(nomisId: Long) = api
    .getPropertyContainerMappingByNomisId(nomisId)
    .awaitSingleOrNull()

  suspend fun getMappingByDpsId(dpsId: String) = api
    .getPropertyContainerMappingByDpsId(dpsId)
    .awaitSingle()

  suspend fun getMappingByDpsIdOrNull(dpsId: String) = api
    .getPropertyContainerMappingByDpsId(dpsId)
    .awaitSingleOrNull()

  suspend fun getNomisLocation(dpsLocationId: String): NomisDpsLocationMapping = externalApi
    .getLocationMappingByDpsId(dpsLocationId)
    .awaitSingle()
}
