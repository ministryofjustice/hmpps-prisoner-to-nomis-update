package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class ContactPersonDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  val contactPersonService: ContactPersonService,
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

  @SqsListener("contactperson", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "contacts-api.contact.created" -> contactPersonService.contactCreated(message.fromJson())
      "contacts-api.contact.deleted" -> contactPersonService.contactDeleted(message.fromJson())
      "contacts-api.prisoner-contact.created" -> contactPersonService.prisonerContactCreated(message.fromJson())
      "contacts-api.contact-address.created" -> contactPersonService.contactAddressCreated(message.fromJson())
      "contacts-api.contact-email.created" -> contactPersonService.contactEmailCreated(message.fromJson())
      "contacts-api.contact-phone.created" -> contactPersonService.contactPhoneCreated(message.fromJson())
      "contacts-api.contact-address-phone.created" -> contactPersonService.contactAddressPhoneCreated(message.fromJson())
      "contacts-api.contact-identity.created" -> contactPersonService.contactIdentityCreated(message.fromJson())
      "contacts-api.contact-restriction.created" -> contactPersonService.contactRestrictionCreated(message.fromJson())
      "contacts-api.contact-restriction.updated" -> contactPersonService.contactRestrictionUpdated(message.fromJson())
      "contacts-api.prisoner-contact-restriction.created" -> contactPersonService.prisonerContactRestrictionCreated(message.fromJson())
      "contacts-api.prisoner-contact-restriction.updated" -> contactPersonService.prisonerContactRestrictionUpdated(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

interface SourcedContactPersonEvent {
  val additionalInformation: SourcedAdditionalData
}
data class ContactCreatedEvent(
  override val additionalInformation: ContactAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactDeletedEvent(
  override val additionalInformation: ContactAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactAdditionalData(
  val contactId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class PrisonerContactCreatedEvent(
  override val additionalInformation: PrisonerContactAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class PrisonerContactAdditionalData(
  val prisonerContactId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactAddressCreatedEvent(
  override val additionalInformation: ContactAddressAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactAddressAdditionalData(
  val contactAddressId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactEmailCreatedEvent(
  override val additionalInformation: ContactEmailAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactEmailAdditionalData(
  val contactEmailId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactPhoneCreatedEvent(
  override val additionalInformation: ContactPhoneAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactPhoneAdditionalData(
  val contactPhoneId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactAddressPhoneCreatedEvent(
  override val additionalInformation: ContactAddressPhoneAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactAddressPhoneAdditionalData(
  val contactAddressPhoneId: Long,
  val contactAddressId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactIdentityCreatedEvent(
  override val additionalInformation: ContactIdentityAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactIdentityAdditionalData(
  val contactIdentityId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class PrisonerContactRestrictionCreatedEvent(
  override val additionalInformation: PrisonerContactRestrictionAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class PrisonerContactRestrictionUpdatedEvent(
  override val additionalInformation: PrisonerContactRestrictionAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class PrisonerContactRestrictionAdditionalData(
  val prisonerContactRestrictionId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactRestrictionCreatedEvent(
  override val additionalInformation: ContactRestrictionAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactRestrictionUpdatedEvent(
  override val additionalInformation: ContactRestrictionAdditionalData,
  val personReference: ContactIdentifiers,
) : SourcedContactPersonEvent

data class ContactRestrictionAdditionalData(
  val contactRestrictionId: Long,
  override val source: String = "DPS",
) : SourcedAdditionalData

data class ContactIdentifiers(val identifiers: List<ContactPersonReference>)
data class ContactPersonReference(val type: String, val value: String)

interface SourcedAdditionalData {
  val source: String
}
