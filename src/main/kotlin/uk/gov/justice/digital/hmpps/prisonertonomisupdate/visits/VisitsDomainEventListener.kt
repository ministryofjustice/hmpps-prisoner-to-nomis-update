package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
class VisitsDomainEventListener(
  private val visitsService: VisitsService,
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
) : DomainEventListener(
  service = visitsService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("visit", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-hmpps_prisoner_to_nomis_visit_queue", kind = SpanKind.SERVER)
  fun onMessage(rawMessage: String): CompletableFuture<Void> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "prison-visit.booked" -> visitsService.createVisit(objectMapper.readValue(message))
      "prison-visit.cancelled" -> visitsService.cancelVisit(objectMapper.readValue(message))
      "prison-visit.changed" -> visitsService.updateVisit(objectMapper.readValue(message))

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
