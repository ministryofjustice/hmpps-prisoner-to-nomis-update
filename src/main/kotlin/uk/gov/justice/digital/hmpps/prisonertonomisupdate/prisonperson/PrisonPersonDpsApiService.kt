package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.dpsmodel.PrisonPersonDto

class PrisonPersonDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {
  suspend fun getPrisonPerson(prisonerNumber: String): PrisonPersonDto = webClient
    .get()
    .uri("/prisoners/{prisonerNumber}", prisonerNumber)
    .retrieve()
    .awaitBody()
}
