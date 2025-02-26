package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsSyncService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class ContactPersonDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  val contactPersonService: ContactPersonService,
  val profileDetailsService: ContactPersonProfileDetailsSyncService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = contactPersonService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("personalrelationships", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "contacts-api.contact.created" -> contactPersonService.contactCreated(message.fromJson())
      "contacts-api.contact.deleted" -> contactPersonService.contactDeleted(message.fromJson())
      "contacts-api.contact.updated" -> contactPersonService.contactUpdated(message.fromJson())
      "contacts-api.prisoner-contact.created" -> contactPersonService.prisonerContactCreated(message.fromJson())
      "contacts-api.prisoner-contact.updated" -> contactPersonService.prisonerContactUpdated(message.fromJson())
      "contacts-api.contact-address.created" -> contactPersonService.contactAddressCreated(message.fromJson())
      "contacts-api.contact-address.updated" -> contactPersonService.contactAddressUpdated(message.fromJson())
      "contacts-api.contact-address.deleted" -> contactPersonService.contactAddressDeleted(message.fromJson())
      "contacts-api.contact-email.created" -> contactPersonService.contactEmailCreated(message.fromJson())
      "contacts-api.contact-email.updated" -> contactPersonService.contactEmailUpdated(message.fromJson())
      "contacts-api.contact-email.deleted" -> contactPersonService.contactEmailDeleted(message.fromJson())
      "contacts-api.contact-phone.created" -> contactPersonService.contactPhoneCreated(message.fromJson())
      "contacts-api.contact-phone.updated" -> contactPersonService.contactPhoneUpdated(message.fromJson())
      "contacts-api.contact-phone.deleted" -> contactPersonService.contactPhoneDeleted(message.fromJson())
      "contacts-api.contact-address-phone.created" -> contactPersonService.contactAddressPhoneCreated(message.fromJson())
      "contacts-api.contact-address-phone.updated" -> contactPersonService.contactAddressPhoneUpdated(message.fromJson())
      "contacts-api.contact-address-phone.deleted" -> contactPersonService.contactAddressPhoneDeleted(message.fromJson())
      "contacts-api.contact-identity.created" -> contactPersonService.contactIdentityCreated(message.fromJson())
      "contacts-api.contact-identity.updated" -> contactPersonService.contactIdentityUpdated(message.fromJson())
      "contacts-api.contact-identity.deleted" -> contactPersonService.contactIdentityDeleted(message.fromJson())
      "contacts-api.contact-restriction.created" -> contactPersonService.contactRestrictionCreated(message.fromJson())
      "contacts-api.contact-restriction.updated" -> contactPersonService.contactRestrictionUpdated(message.fromJson())
      "contacts-api.prisoner-contact-restriction.created" -> contactPersonService.prisonerContactRestrictionCreated(message.fromJson())
      "contacts-api.prisoner-contact-restriction.updated" -> contactPersonService.prisonerContactRestrictionUpdated(message.fromJson())
      "personal-relationships-api.domestic-status.created" -> profileDetailsService.syncDomesticStatus(message.fromJson())
      "personal-relationships-api.number-of-children.created" -> profileDetailsService.syncNumberOfChildren(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

interface SourcedContactPersonEvent {
  val additionalInformation: SourcedAdditionalData
}
data class ContactCreatedEvent(
  override val additionalInformation: ContactAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactUpdatedEvent(
  override val additionalInformation: ContactAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactDeletedEvent(
  override val additionalInformation: ContactAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactAdditionalData(
  val contactId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class PrisonerContactCreatedEvent(
  override val additionalInformation: PrisonerContactAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class PrisonerContactUpdatedEvent(
  override val additionalInformation: PrisonerContactAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class PrisonerContactAdditionalData(
  val prisonerContactId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactAddressCreatedEvent(
  override val additionalInformation: ContactAddressAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactAddressUpdateEvent(
  override val additionalInformation: ContactAddressAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactAddressDeletedEvent(
  override val additionalInformation: ContactAddressAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactAddressAdditionalData(
  val contactAddressId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactEmailCreatedEvent(
  override val additionalInformation: ContactEmailAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactEmailUpdatedEvent(
  override val additionalInformation: ContactEmailAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactEmailDeletedEvent(
  override val additionalInformation: ContactEmailAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactEmailAdditionalData(
  val contactEmailId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactPhoneCreatedEvent(
  override val additionalInformation: ContactPhoneAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactPhoneUpdatedEvent(
  override val additionalInformation: ContactPhoneAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactPhoneDeletedEvent(
  override val additionalInformation: ContactPhoneAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactPhoneAdditionalData(
  val contactPhoneId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactAddressPhoneCreatedEvent(
  override val additionalInformation: ContactAddressPhoneAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactAddressPhoneUpdatedEvent(
  override val additionalInformation: ContactAddressPhoneAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactAddressPhoneDeletedEvent(
  override val additionalInformation: ContactAddressPhoneAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactAddressPhoneAdditionalData(
  val contactAddressPhoneId: Long,
  val contactAddressId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactIdentityCreatedEvent(
  override val additionalInformation: ContactIdentityAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactIdentityUpdatedEvent(
  override val additionalInformation: ContactIdentityAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactIdentityDeletedEvent(
  override val additionalInformation: ContactIdentityAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactIdentityAdditionalData(
  val contactIdentityId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class PrisonerContactRestrictionCreatedEvent(
  override val additionalInformation: PrisonerContactRestrictionAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class PrisonerContactRestrictionUpdatedEvent(
  override val additionalInformation: PrisonerContactRestrictionAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class PrisonerContactRestrictionAdditionalData(
  val prisonerContactRestrictionId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactRestrictionCreatedEvent(
  override val additionalInformation: ContactRestrictionAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactRestrictionUpdatedEvent(
  override val additionalInformation: ContactRestrictionAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactRestrictionAdditionalData(
  val contactRestrictionId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactDomesticStatusCreatedEvent(
  override val additionalInformation: ContactDomesticStatusData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactDomesticStatusData(
  val domesticStatusId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactNumberOfChildrenCreatedEvent(
  override val additionalInformation: ContactNumberOfChildrenData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactNumberOfChildrenData(
  val numberOfChildrenId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactIdentifiers(val identifiers: List<ContactPersonReference>)
data class ContactPersonReference(val type: String, val value: String)

interface SourcedAdditionalData {
  val source: String
}

interface ContactIdReferencedEvent {
  val personReference: ContactIdentifiers
}

fun ContactIdReferencedEvent.contactId() = personReference.identifiers.first { it.type == "DPS_CONTACT_ID" }.value.toLong()
