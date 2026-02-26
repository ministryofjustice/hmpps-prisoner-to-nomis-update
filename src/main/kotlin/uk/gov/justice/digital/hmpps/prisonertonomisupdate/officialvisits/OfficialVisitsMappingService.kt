package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.OfficialVisitsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class OfficialVisitsMappingService(
  @Qualifier("mappingWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = OfficialVisitsResourceApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "OfficialVisitsMappingService"),
  )

  suspend fun getVisitByNomisIdsOrNull(nomisVisitId: Long): OfficialVisitMappingDto? = api.prepare(
    api.getVisitMappingByNomisIdRequestConfig(
      nomisVisitId = nomisVisitId,
    ),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound(retrySpec)

  suspend fun getVisitByDpsIdsOrNull(dpsVisitId: Long): OfficialVisitMappingDto? = api.prepare(
    api.getVisitMappingByDpsIdRequestConfig(
      dpsVisitId = dpsVisitId.toString(),
    ),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound(retrySpec)

  suspend fun createVisitMapping(mapping: OfficialVisitMappingDto) = api.prepare(api.createVisitMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getVisitorByDpsIdsOrNull(dpsVisitorId: Long): OfficialVisitorMappingDto? = api.prepare(
    api.getVisitorMappingByDpsIdRequestConfig(
      dpsVisitorId = dpsVisitorId.toString(),
    ),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
