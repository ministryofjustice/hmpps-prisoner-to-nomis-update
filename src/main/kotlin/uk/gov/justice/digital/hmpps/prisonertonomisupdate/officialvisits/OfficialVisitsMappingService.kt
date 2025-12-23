package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.OfficialVisitsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto

@Service
class OfficialVisitsMappingService(@Qualifier("mappingWebClient") webClient: WebClient) {
  private val api = OfficialVisitsResourceApi(webClient)

  suspend fun getByNomisIdsOrNull(nomisVisitId: Long): OfficialVisitMappingDto? = api.prepare(
    api.getVisitMappingByNomisIdsRequestConfig(
      nomisVisitId = nomisVisitId,
    ),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
