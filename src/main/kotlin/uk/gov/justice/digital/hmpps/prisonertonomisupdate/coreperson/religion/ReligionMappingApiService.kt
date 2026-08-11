package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto

@Service
class ReligionMappingApiService(@Qualifier("mappingWebClient") private val webClient: WebClient) {
  suspend fun getByCprIds(crpReligionIds: List<String>): List<ReligionMappingDto> = webClient.get()
    .uri {
      it.path("/mapping/religion/cpr-ids").queryParam(
        "ids",
        *crpReligionIds.toTypedArray(),
      ).build()
    }
    .retrieve()
    .awaitBody()
}
