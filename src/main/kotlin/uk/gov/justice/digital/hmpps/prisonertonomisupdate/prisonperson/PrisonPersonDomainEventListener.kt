package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListenerNoMapping
import java.util.concurrent.CompletableFuture

@Service
class PrisonPersonDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val prisonPersonService: PrisonPersonService,
  telemetryClient: TelemetryClient,
) : DomainEventListenerNoMapping(
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("prisonperson", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_to_nomis_prisonperson_queue", kind = SpanKind.SERVER)
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "prison-person.physical-attributes.updated" -> prisonPersonService.updatePhysicalAttributesEvent(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
