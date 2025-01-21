package uk.gov.justice.digital.hmpps.prisonertonomisupdate.corporate

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
class CorporateDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  corporateService: CorporateService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = corporateService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Suppress("LoggingSimilarMessage")
  @SqsListener("contactperson", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "contacts-api.organisation.created" -> log.debug("Received event $eventType")
      "contacts-api.organisation.updated" -> log.debug("Received event $eventType")
      "contacts-api.organisation.deleted" -> log.debug("Received event $eventType")
      "contacts-api.organisation-type.created" -> log.debug("Received event $eventType")
      "contacts-api.organisation-type.updated" -> log.debug("Received event $eventType")
      "contacts-api.organisation-type.deleted" -> log.debug("Received event $eventType")
      "contacts-api.organisation-address.created" -> log.debug("Received event $eventType")
      "contacts-api.organisation-address.updated" -> log.debug("Received event $eventType")
      "contacts-api.organisation-address.deleted" -> log.debug("Received event $eventType")
      "contacts-api.organisation-email.created" -> log.debug("Received event $eventType")
      "contacts-api.organisation-email.updated" -> log.debug("Received event $eventType")
      "contacts-api.organisation-email.deleted" -> log.debug("Received event $eventType")
      "contacts-api.organisation-web-address.created" -> log.debug("Received event $eventType")
      "contacts-api.organisation-web-address.updated" -> log.debug("Received event $eventType")
      "contacts-api.organisation-web-address.deleted" -> log.debug("Received event $eventType")
      "contacts-api.organisation-phone.created" -> log.debug("Received event $eventType")
      "contacts-api.organisation-phone.updated" -> log.debug("Received event $eventType")
      "contacts-api.organisation-phone.deleted" -> log.debug("Received event $eventType")
      "contacts-api.organisation-address-phone.created" -> log.debug("Received event $eventType")
      "contacts-api.organisation-address-phone.updated" -> log.debug("Received event $eventType")
      "contacts-api.organisation-address-phone.deleted" -> log.debug("Received event $eventType")

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
