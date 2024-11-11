package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto

@Service
class ContactPersonMappingApiService(@Qualifier("mappingWebClient") val webClient: WebClient) {
  suspend fun getByDpsContactIdOrNull(dpsContactId: Long): PersonMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/person/dps-contact-id/{dpsContactId}",
      dpsContactId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createPersonMapping(mappings: PersonMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/person")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
}
