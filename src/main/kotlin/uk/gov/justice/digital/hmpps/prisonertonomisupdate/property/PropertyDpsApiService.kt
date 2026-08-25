package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.api.SyncWithNOMISApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PageUUID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PropertyContainerDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.UUID

@Service
class PropertyDpsApiService(
  @Qualifier("propertyApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = SyncWithNOMISApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "PropertyDpsApiService"),
  )

  suspend fun getProperty(dpsId: UUID): PropertyContainerDto = api
    .getSyncedContainerById(dpsId)
    .awaitSingle()

  suspend fun getPropertyOrNull(dpsId: UUID): PropertyContainerDto? = api
    .getSyncedContainerById(dpsId)
    .awaitBodyOrNullForNotFound(retrySpec)

  suspend fun getPageOfDpsIds(page: Int, pageSize: Int): PageUUID = api
    .getAllIds(page, pageSize)
    .retryWhen(retrySpec)
    .awaitSingle()
}
