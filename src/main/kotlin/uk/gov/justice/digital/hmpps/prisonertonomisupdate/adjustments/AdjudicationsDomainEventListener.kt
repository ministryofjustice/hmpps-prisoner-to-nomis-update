package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjustments

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class AdjudicationsDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  adjudicationsService: AdjudicationsService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = adjudicationsService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("adjudication", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-dev-hmpps_prisoner_to_nomis_adjudication_queue", kind = SpanKind.SERVER)
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void> = onDomainEvent(rawMessage) { eventType, _ ->
    when (eventType) {
      "adjudication.report.created" ->
        log.info("Ignoring adjudication.report.created event")

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
