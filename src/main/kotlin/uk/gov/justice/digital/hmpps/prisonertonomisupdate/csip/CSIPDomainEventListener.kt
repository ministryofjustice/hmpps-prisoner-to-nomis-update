package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

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
class CSIPDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val csipService: CSIPService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = csipService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("csip", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_to_nomis_csip_queue", kind = SpanKind.SERVER)
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "csip.record.created" -> log.info("Received csip.record.created event")
      "csip.record.updated" -> log.info("Received csip.record.updated event")
      "csip.record.deleted" -> log.info("Received csip.record.deleted event")
      "csip.contributory-factor.created" -> log.info("Received csip.contributory-factor.created event")
      "csip.contributory-factor.deleted" -> log.info("Received csip.contributory-factor.deleted event")
      "csip.interview.created" -> log.info("Received csip.interview.created event")
      "csip.interview.deleted" -> log.info("Received csip.interview.deleted event")
      "csip.identified-need.created" -> log.info("Received csip.identified-need.created event")
      "csip.identified-need.deleted" -> log.info("Received csip.identified-need.deleted event")
      "csip.review.created" -> log.info("Received csip.review.created event")
      "csip.review.deleted" -> log.info("Received csip.review.deleted event")
      "csip.attendee.created" -> log.info("Received csip.attendee.created event")
      "csip.attendee.deleted" -> log.info("Received csip.attendee.deleted event")

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
