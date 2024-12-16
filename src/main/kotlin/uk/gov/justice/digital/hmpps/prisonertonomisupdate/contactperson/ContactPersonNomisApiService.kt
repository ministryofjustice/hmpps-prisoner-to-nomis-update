package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateContactPersonRestrictionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonRequest

@Service
class ContactPersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun createPerson(request: CreatePersonRequest): CreatePersonResponse = webClient.post()
    .uri(
      "/persons",
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun deletePerson(personId: Long) {
    webClient.delete()
      .uri(
        "/persons/{personId}",
        personId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun updatePerson(personId: Long, request: UpdatePersonRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}",
        personId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createPersonContact(personId: Long, request: CreatePersonContactRequest): CreatePersonContactResponse = webClient.post()
    .uri(
      "/persons/{personId}/contact",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updatePersonContact(personId: Long, contactId: Long, request: UpdatePersonContactRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/contact/{contactId}",
        personId,
        contactId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createPersonAddress(personId: Long, request: CreatePersonAddressRequest): CreatePersonAddressResponse = webClient.post()
    .uri(
      "/persons/{personId}/address",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updatePersonAddress(personId: Long, addressId: Long, request: UpdatePersonAddressRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/address/{addressId}",
        personId,
        addressId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createPersonEmail(personId: Long, request: CreatePersonEmailRequest): CreatePersonEmailResponse = webClient.post()
    .uri(
      "/persons/{personId}/email",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createPersonPhone(personId: Long, request: CreatePersonPhoneRequest): CreatePersonPhoneResponse = webClient.post()
    .uri(
      "/persons/{personId}/phone",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
  suspend fun updatePersonPhone(personId: Long, phoneId: Long, request: UpdatePersonPhoneRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/phone/{phoneId}",
        personId,
        phoneId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createPersonAddressPhone(personId: Long, addressId: Long, request: CreatePersonPhoneRequest): CreatePersonPhoneResponse = webClient.post()
    .uri(
      "/persons/{personId}/address/{addressId}/phone",
      personId,
      addressId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
  suspend fun updatePersonAddressPhone(personId: Long, addressId: Long, phoneId: Long, request: UpdatePersonPhoneRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/address/{addressId}/phone/{phoneId}",
        personId,
        addressId,
        phoneId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createPersonIdentifier(personId: Long, request: CreatePersonIdentifierRequest): CreatePersonIdentifierResponse = webClient.post()
    .uri(
      "/persons/{personId}/identifier",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createContactRestriction(personId: Long, contactId: Long, request: CreateContactPersonRestrictionRequest): CreateContactPersonRestrictionResponse = webClient.post()
    .uri(
      "/persons/{personId}/contact/{contactId}/restriction",
      personId,
      contactId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateContactRestriction(personId: Long, contactId: Long, contactRestrictionId: Long, request: UpdateContactPersonRestrictionRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/contact/{contactId}/restriction/{contactRestrictionId}",
        personId,
        contactId,
        contactRestrictionId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createPersonRestriction(personId: Long, request: CreateContactPersonRestrictionRequest): CreateContactPersonRestrictionResponse = webClient.post()
    .uri(
      "/persons/{personId}/restriction",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updatePersonRestriction(personId: Long, personRestrictionId: Long, request: UpdateContactPersonRestrictionRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/restriction/{personRestrictionId}",
        personId,
        personRestrictionId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
}
