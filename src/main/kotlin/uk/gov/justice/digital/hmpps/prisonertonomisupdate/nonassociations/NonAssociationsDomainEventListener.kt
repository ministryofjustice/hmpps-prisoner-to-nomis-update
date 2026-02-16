package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class NonAssociationsDomainEventListener(
  private val nonAssociationsService: NonAssociationsService,
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = nonAssociationsService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "nonassociations",
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("nonassociation", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(rawMessage: String): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "non-associations.created" -> nonAssociationsService.createNonAssociation(message.fromJson())
      "non-associations.amended" -> nonAssociationsService.amendNonAssociation(message.fromJson())
      "non-associations.closed" -> nonAssociationsService.closeNonAssociation(message.fromJson())
      "non-associations.deleted" -> nonAssociationsService.deleteNonAssociation(message.fromJson())
      "prison-offender-events.prisoner.merged" -> nonAssociationsService.processMerge(message.fromJson())
      "prison-offender-events.prisoner.booking.moved" -> nonAssociationsService.processBookingMoved(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
