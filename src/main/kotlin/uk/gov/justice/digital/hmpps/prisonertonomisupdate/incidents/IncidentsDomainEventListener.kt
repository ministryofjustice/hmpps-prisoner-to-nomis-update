package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListenerNoMapping
import java.util.concurrent.CompletableFuture

@Service
class IncidentsDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val incidentsService: IncidentsService,
  telemetryClient: TelemetryClient,
) : DomainEventListenerNoMapping(
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "incidents",
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("incidents", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "incident.report.created", "incident.report.amended" -> incidentsService.incidentUpsert(message.fromJson())
      "incident.report.deleted" -> incidentsService.incidentDeleted(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
