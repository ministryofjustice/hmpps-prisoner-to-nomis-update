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
  organisationsService: OrganisationsService,
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
      "organisations-api.organisation.created" -> log.debug("Received event $eventType")
      "organisations-api.organisation.updated" -> log.debug("Received event $eventType")
      "organisations-api.organisation.deleted" -> log.debug("Received event $eventType")
      "organisations-api.organisation-type.created" -> log.debug("Received event $eventType")
      "organisations-api.organisation-type.updated" -> log.debug("Received event $eventType")
      "organisations-api.organisation-type.deleted" -> log.debug("Received event $eventType")
      "organisations-api.organisation-address.created" -> log.debug("Received event $eventType")
      "organisations-api.organisation-address.updated" -> log.debug("Received event $eventType")
      "organisations-api.organisation-address.deleted" -> log.debug("Received event $eventType")
      "organisations-api.organisation-email.created" -> log.debug("Received event $eventType")
      "organisations-api.organisation-email.updated" -> log.debug("Received event $eventType")
      "organisations-api.organisation-email.deleted" -> log.debug("Received event $eventType")
      "organisations-api.organisation-web-address.created" -> log.debug("Received event $eventType")
      "organisations-api.organisation-web-address.updated" -> log.debug("Received event $eventType")
      "organisations-api.organisation-web-address.deleted" -> log.debug("Received event $eventType")
      "organisations-api.organisation-phone.created" -> log.debug("Received event $eventType")
      "organisations-api.organisation-phone.updated" -> log.debug("Received event $eventType")
      "organisations-api.organisation-phone.deleted" -> log.debug("Received event $eventType")
      "organisations-api.organisation-address-phone.created" -> log.debug("Received event $eventType")
      "organisations-api.organisation-address-phone.updated" -> log.debug("Received event $eventType")
      "organisations-api.organisation-address-phone.deleted" -> log.debug("Received event $eventType")

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
