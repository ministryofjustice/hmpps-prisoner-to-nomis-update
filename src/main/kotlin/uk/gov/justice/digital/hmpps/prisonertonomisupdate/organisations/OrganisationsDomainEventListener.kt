package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

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
class OrganisationsDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  val organisationsService: OrganisationsService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = organisationsService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Suppress("LoggingSimilarMessage")
  @SqsListener("organisations", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "organisations-api.organisation.created" -> organisationsService.organisationCreated(message.fromJson())
      "organisations-api.organisation.updated" -> organisationsService.organisationUpdated(message.fromJson())
      "organisations-api.organisation.deleted" -> organisationsService.organisationDeleted(message.fromJson())
      "organisations-api.organisation-types.updated" -> organisationsService.organisationTypesUpdated(message.fromJson())
      "organisations-api.organisation-address.created" -> organisationsService.organisationAddressCreated(message.fromJson())
      "organisations-api.organisation-address.updated" -> organisationsService.organisationAddressUpdated(message.fromJson())
      "organisations-api.organisation-address.deleted" -> organisationsService.organisationAddressDeleted(message.fromJson())
      "organisations-api.organisation-email.created" -> organisationsService.organisationEmailCreated(message.fromJson())
      "organisations-api.organisation-email.updated" -> organisationsService.organisationEmailUpdated(message.fromJson())
      "organisations-api.organisation-email.deleted" -> organisationsService.organisationEmailDeleted(message.fromJson())
      "organisations-api.organisation-web.created" -> organisationsService.organisationWebCreated(message.fromJson())
      "organisations-api.organisation-web.updated" -> organisationsService.organisationWebUpdated(message.fromJson())
      "organisations-api.organisation-web.deleted" -> organisationsService.organisationWebDeleted(message.fromJson())
      "organisations-api.organisation-phone.created" -> organisationsService.organisationPhoneCreated(message.fromJson())
      "organisations-api.organisation-phone.updated" -> organisationsService.organisationPhoneUpdated(message.fromJson())
      "organisations-api.organisation-phone.deleted" -> organisationsService.organisationPhoneDeleted(message.fromJson())
      "organisations-api.organisation-address-phone.created" -> organisationsService.organisationAddressPhoneCreated(message.fromJson())
      "organisations-api.organisation-address-phone.updated" -> organisationsService.organisationAddressPhoneUpdated(message.fromJson())
      "organisations-api.organisation-address-phone.deleted" -> organisationsService.organisationAddressPhoneDeleted(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

// TODO - create concrete classes where appropriate
interface SourcedOrganisationsEvent {
  val additionalInformation: OrganisationAdditionalData
  val organisationId: Long
    get() = additionalInformation.organisationId
}

interface SourcedOrganisationChildEvent {
  val additionalInformation: OrganisationChildAdditionalData
  val organisationId: Long
    get() = additionalInformation.organisationId
}

interface SourcedAdditionalData {
  val source: String
}
interface OrganisationAdditionalData : SourcedAdditionalData {
  val organisationId: Long
}
interface OrganisationChildAdditionalData : OrganisationAdditionalData {
  val identifier: Long
}

data class OrganisationEvent(override val additionalInformation: OrganisationAdditionalData) : SourcedOrganisationsEvent
data class OrganisationAddressEvent(override val additionalInformation: OrganisationChildAdditionalData) : SourcedOrganisationChildEvent {
  val addressId: Long
    get() = additionalInformation.identifier
}
data class OrganisationEmailEvent(override val additionalInformation: OrganisationChildAdditionalData) : SourcedOrganisationChildEvent {
  val emailId: Long
    get() = additionalInformation.identifier
}
data class OrganisationWebEvent(override val additionalInformation: OrganisationChildAdditionalData) : SourcedOrganisationChildEvent {
  val webId: Long
    get() = additionalInformation.identifier
}
data class OrganisationPhoneEvent(override val additionalInformation: OrganisationChildAdditionalData) : SourcedOrganisationChildEvent {
  val phoneId: Long
    get() = additionalInformation.identifier
}
data class OrganisationAddressPhoneEvent(override val additionalInformation: OrganisationChildAdditionalData) : SourcedOrganisationChildEvent {
  val addressPhoneId: Long
    get() = additionalInformation.identifier
}
