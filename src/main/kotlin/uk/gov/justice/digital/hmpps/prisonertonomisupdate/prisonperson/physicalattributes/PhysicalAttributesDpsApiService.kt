package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PhysicalAttributesSyncDto

@Service
class PhysicalAttributesDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {
  suspend fun getPhysicalAttributes(prisonerNumber: String): PhysicalAttributesSyncDto? =
    webClient
      .get()
      .uri("/sync/prisoners/{prisonerNumber}/physical-attributes", prisonerNumber)
      .retrieve()
      .awaitBodyOrNullForNotFound<PhysicalAttributesSyncDto>()
}
