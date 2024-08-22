package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.model.PhysicalAttributesDto

@Service
class PrisonPersonDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {
  suspend fun getPhysicalAttributes(prisonerNumber: String): PhysicalAttributesDto? = webClient
    .get()
    .uri("/prisoners/{prisonerNumber}/physical-attributes", prisonerNumber)
    .retrieve()
    .awaitBodyOrNullForNotFound<PhysicalAttributesDto>()
}
