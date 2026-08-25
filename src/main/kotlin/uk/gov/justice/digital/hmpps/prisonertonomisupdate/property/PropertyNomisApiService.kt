package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PropertyResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePropertyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerCreateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerGetResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerUpdateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class PropertyNomisApiService(
  @Qualifier("nomisApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = PropertyResourceApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "PropertyNomisService"),
  )

  suspend fun createProperty(request: PropertyContainerCreateRequest): CreatePropertyResponse = api
    .create(request)
    .awaitSingle()

  suspend fun updateProperty(id: Long, request: PropertyContainerUpdateRequest) {
    api.update(id, request).awaitSingle()
  }

  suspend fun getPropertyContainer(id: Long): PropertyContainerGetResponse = api
    .get(id)
    .retryWhen(retrySpec)
    .awaitSingle()

  suspend fun getIdRanges(pageSize: Int): List<Long> = api
    .getPropertyContainerIdRanges(pageSize)
    .retryWhen(retrySpec)
    .awaitSingle()

  suspend fun getIdentifiersInRange(startId: Long, endId: Long): List<Long> = api
    .getIdentifiersInRange(startId, endId)
    .retryWhen(retrySpec)
    .awaitSingle()
}
