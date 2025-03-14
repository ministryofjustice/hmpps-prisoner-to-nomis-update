package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateContactPersonRestrictionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonEmploymentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class ContactPersonNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "ContactPersonNomisApiService"),
  )

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
  suspend fun deletePersonAddress(personId: Long, addressId: Long) {
    webClient.delete()
      .uri(
        "/persons/{personId}/address/{addressId}",
        personId,
        addressId,
      )
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

  suspend fun updatePersonEmail(personId: Long, emailId: Long, request: UpdatePersonEmailRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/email/{emailId}",
        personId,
        emailId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deletePersonEmail(personId: Long, emailId: Long) {
    webClient.delete()
      .uri(
        "/persons/{personId}/email/{emailId}",
        personId,
        emailId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

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

  suspend fun deletePersonPhone(personId: Long, phoneId: Long) {
    webClient.delete()
      .uri(
        "/persons/{personId}/phone/{phoneId}",
        personId,
        phoneId,
      )
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
  suspend fun deletePersonAddressPhone(personId: Long, addressId: Long, phoneId: Long) {
    webClient.delete()
      .uri(
        "/persons/{personId}/address/{addressId}/phone/{phoneId}",
        personId,
        addressId,
        phoneId,
      )
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

  suspend fun updatePersonIdentifier(personId: Long, sequence: Long, request: UpdatePersonIdentifierRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/identifier/{sequence}",
        personId,
        sequence,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deletePersonIdentifier(personId: Long, sequence: Long) {
    webClient.delete()
      .uri(
        "/persons/{personId}/identifier/{sequence}",
        personId,
        sequence,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createPersonEmployment(personId: Long, request: CreatePersonEmploymentRequest): CreatePersonEmploymentResponse = webClient.post()
    .uri(
      "/persons/{personId}/employment",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updatePersonEmployment(personId: Long, sequence: Long, request: UpdatePersonEmploymentRequest) {
    webClient.put()
      .uri(
        "/persons/{personId}/employment/{sequence}",
        personId,
        sequence,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deletePersonEmployment(personId: Long, sequence: Long) {
    webClient.delete()
      .uri(
        "/persons/{personId}/employment/{sequence}",
        personId,
        sequence,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

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

  suspend fun getContactsForPrisoner(offenderNo: String): PrisonerWithContacts = webClient.get()
    .uri("/prisoners/{offenderNo}/contacts?active-only={activeOnly}&latest-booking-only={latestBookingOnly}", offenderNo, true, true)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)
}
