package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.IncentiveMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncentiveMappingDto

@Service
class IncentivesMappingService(
  @Qualifier("mappingWebClient") webClient: WebClient,
) {
  private val incentiveMappingResourceApi = IncentiveMappingResourceApi(webClient)

  suspend fun createMapping(request: IncentiveMappingDto) {
    incentiveMappingResourceApi.prepare(
      incentiveMappingResourceApi.createMapping8RequestConfig(request),
    )
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenIncentiveIdOrNull(incentiveId: Long): IncentiveMappingDto? = incentiveMappingResourceApi.prepare(
    incentiveMappingResourceApi.getIncentiveMappingGivenIncentiveIdRequestConfig(incentiveId),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
