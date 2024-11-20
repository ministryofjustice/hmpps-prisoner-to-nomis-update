package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncPrisonerContact

@Service
class ContactPersonDpsApiService(@Qualifier("contactPersonApiWebClient") private val webClient: WebClient) {
  suspend fun getContact(contactId: Long): SyncContact = webClient.get()
    .uri("/sync/contact/{contactId}", contactId)
    .retrieve()
    .awaitBody()

  suspend fun getPrisonerContact(prisonerContactId: Long): SyncPrisonerContact = webClient.get()
    .uri("/sync/prisoner-contact/{prisonerContactId}", prisonerContactId)
    .retrieve()
    .awaitBody()
}
