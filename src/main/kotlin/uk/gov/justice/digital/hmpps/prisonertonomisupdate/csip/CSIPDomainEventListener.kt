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
      "person.csip-record.created" -> csipService.createCSIPReport(message.fromJson())
      "person.csip-record.updated" -> csipService.updateCSIPReport(message.fromJson())
      "person.csip-record.deleted" -> csipService.deleteCsipReport(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
