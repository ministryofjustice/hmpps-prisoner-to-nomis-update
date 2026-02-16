package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileDetailsSyncService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class ContactPersonDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  val contactPersonService: ContactPersonService,
  val profileDetailsService: ContactPersonProfileDetailsSyncService,
  val prisonerRestrictionsService: PrisonerRestrictionsService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = contactPersonService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "personcontacts",
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
      "contacts-api.prisoner-contact.deleted" -> contactPersonService.prisonerContactDeleted(message.fromJson())
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
      "contacts-api.employment.created" -> contactPersonService.contactEmploymentCreated(message.fromJson())
      "contacts-api.employment.updated" -> contactPersonService.contactEmploymentUpdated(message.fromJson())
      "contacts-api.employment.deleted" -> contactPersonService.contactEmploymentDeleted(message.fromJson())
      "contacts-api.contact-restriction.created" -> contactPersonService.contactRestrictionCreated(message.fromJson())
      "contacts-api.contact-restriction.updated" -> contactPersonService.contactRestrictionUpdated(message.fromJson())
      "contacts-api.prisoner-contact-restriction.created" -> contactPersonService.prisonerContactRestrictionCreated(message.fromJson())
      "contacts-api.prisoner-contact-restriction.updated" -> contactPersonService.prisonerContactRestrictionUpdated(message.fromJson())
      "personal-relationships-api.domestic-status.created" -> profileDetailsService.syncDomesticStatus(message.fromJson())
      "personal-relationships-api.number-of-children.created" -> profileDetailsService.syncNumberOfChildren(message.fromJson())
      "personal-relationships-api.domestic-status.deleted" -> profileDetailsService.deleteDomesticStatus(message.fromJson())
      "personal-relationships-api.number-of-children.deleted" -> profileDetailsService.deleteNumberOfChildren(message.fromJson())
      "prisoner-offender-search.prisoner.received" -> profileDetailsService.readmissionSwitchBooking(message.fromJson())
      "personal-relationships-api.prisoner-restriction.created" -> prisonerRestrictionsService.restrictionCreated(message.fromJson())
      "personal-relationships-api.prisoner-restriction.updated" -> prisonerRestrictionsService.restrictionUpdated(message.fromJson())
      "personal-relationships-api.prisoner-restriction.deleted" -> prisonerRestrictionsService.restrictionDeleted(message.fromJson())
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

data class PrisonerContactDeletedEvent(
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

data class ContactEmploymentAdditionalData(
  val employmentId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactEmploymentCreatedEvent(
  override val additionalInformation: ContactEmploymentAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactEmploymentUpdatedEvent(
  override val additionalInformation: ContactEmploymentAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactEmploymentDeletedEvent(
  override val additionalInformation: ContactEmploymentAdditionalData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

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

data class ContactDomesticStatusDeletedEvent(
  override val additionalInformation: ContactDomesticStatusData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactNumberOfChildrenCreatedEvent(
  override val additionalInformation: ContactNumberOfChildrenData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ContactNumberOfChildrenData(
  val prisonerNumberOfChildrenId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactNumberOfChildrenDeletedEvent(
  override val additionalInformation: ContactNumberOfChildrenData,
  override val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent,
  ContactIdReferencedEvent

data class ReadmissionSwitchBookingEvent(
  val additionalInformation: ReadmissionSwitchBookingData,
)

data class ReadmissionSwitchBookingData(
  val nomsNumber: String,
  val reason: String,
  val prisonId: String,
)

data class ContactIdentifiers(val identifiers: List<ContactPersonReference>)
data class ContactPersonReference(val type: String, val value: String)

interface SourcedAdditionalData {
  val source: String
}

interface ContactIdReferencedEvent {
  val personReference: ContactIdentifiers
}

fun ContactIdReferencedEvent.contactId() = personReference.identifiers.first { it.type == "DPS_CONTACT_ID" }.value.toLong()

data class PrisonerRestrictionAdditionalData(
  val prisonerRestrictionId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

interface SourcedPrisonerRestrictionEvent {
  val additionalInformation: PrisonerRestrictionAdditionalData
}

data class PrisonerIdentifiers(val identifiers: List<PrisonerReference>)
data class PrisonerReference(val type: String, val value: String)

data class PrisonerRestrictionEvent(
  override val additionalInformation: PrisonerRestrictionAdditionalData,
  override val personReference: PrisonerIdentifiers,
) : SourcedPrisonerRestrictionEvent,
  PrisonerReferencedEvent

interface PrisonerReferencedEvent {
  val personReference: PrisonerIdentifiers
}

fun PrisonerReferencedEvent.prisonerNumber() = personReference.identifiers.first { it.type == "NOMS" }.value
