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
      "contacts-api.prisoner-contact.created" -> contactPersonService.prisonerContactCreated(message.fromJson())
      "contacts-api.contact-address.created" -> contactPersonService.contactAddressCreated(message.fromJson())
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

data class ContactIdentifiers(val identifiers: List<ContactPersonReference>)
data class ContactPersonReference(val type: String, val value: String)

interface SourcedAdditionalData {
  val source: String
}
