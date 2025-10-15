package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

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
class LocationsDomainEventListener(
  private val locationsService: LocationsService,
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = locationsService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "locations",
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("location", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(rawMessage: String): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "location.inside.prison.created" -> locationsService.createLocation(message.fromJson())
      "location.inside.prison.amended" -> locationsService.amendLocation(message.fromJson())
      "location.inside.prison.deactivated" -> locationsService.deactivateLocation(message.fromJson())
      "location.inside.prison.reactivated" -> locationsService.reactivateLocation(message.fromJson())
      "location.inside.prison.deleted" -> locationsService.softDeleteLocation(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
