package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncPrisonerContactRestriction

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

  suspend fun getContactAddress(contactAddressId: Long): SyncContactAddress = webClient.get()
    .uri("/sync/contact-address/{contactAddressId}", contactAddressId)
    .retrieve()
    .awaitBody()

  suspend fun getContactEmail(contactEmailId: Long): SyncContactEmail = webClient.get()
    .uri("/sync/contact-email/{contactEmailId}", contactEmailId)
    .retrieve()
    .awaitBody()

  suspend fun getContactPhone(contactPhoneId: Long): SyncContactPhone = webClient.get()
    .uri("/sync/contact-phone/{contactPhoneId}", contactPhoneId)
    .retrieve()
    .awaitBody()

  suspend fun getContactAddressPhone(contactAddressPhoneId: Long): SyncContactAddressPhone = webClient.get()
    .uri("/sync/contact-address-phone/{contactAddressPhoneId}", contactAddressPhoneId)
    .retrieve()
    .awaitBody()

  suspend fun getContactIdentity(contactIdentityId: Long): SyncContactIdentity = webClient.get()
    .uri("/sync/contact-identity/{contactIdentityId}", contactIdentityId)
    .retrieve()
    .awaitBody()

  suspend fun getContactRestriction(contactRestrictionId: Long): SyncContactRestriction = webClient.get()
    .uri("/sync/contact-restriction/{contactRestrictionId}", contactRestrictionId)
    .retrieve()
    .awaitBody()

  suspend fun getPrisonerContactRestriction(prisonerContactRestrictionId: Long): SyncPrisonerContactRestriction = webClient.get()
    .uri("/sync/prisoner-contact-restriction/{prisonerContactRestrictionId}", prisonerContactRestrictionId)
    .retrieve()
    .awaitBody()
}
