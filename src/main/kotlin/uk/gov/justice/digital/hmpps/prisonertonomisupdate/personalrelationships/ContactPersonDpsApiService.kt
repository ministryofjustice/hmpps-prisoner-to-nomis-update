package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactRestrictionsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactSummaryPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class ContactPersonDpsApiService(@Qualifier("personalRelationshipsApiWebClient") private val webClient: WebClient, retryApiService: RetryApiService) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "ContactPersonDpsApiService"),
  )

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

  suspend fun getContactEmployment(contactEmploymentId: Long): SyncEmployment = webClient.get()
    .uri("/sync/employment/{contactEmploymentId}", contactEmploymentId)
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

  suspend fun getPrisonerContacts(prisonNumber: String): PrisonerContactSummaryPage = webClient.get()
    .uri("/prisoner/{prisonNumber}/contact?page=0&size=10000&active=true", prisonNumber)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getPrisonerContactRestrictions(prisonerContactId: Long): PrisonerContactRestrictionsResponse = webClient.get()
    .uri("/prisoner-contact/{prisonerContactId}/restriction", prisonerContactId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)
}
