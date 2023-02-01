package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage

@Service
class VisitsDomainEventListener(
  private val prisonVisitsService: PrisonVisitsService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("visit", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-hmpps_prisoner_to_nomis_visit_queue", kind = SpanKind.SERVER)
  fun onPrisonerChange(message: String) {
    log.debug("Received visit message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    when (sqsMessage.Type) {
      "Notification" -> {
        val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
        if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
          "prison-visit.booked" -> prisonVisitsService.createVisit(objectMapper.readValue(sqsMessage.Message))
          "prison-visit.cancelled" -> prisonVisitsService.cancelVisit(objectMapper.readValue(sqsMessage.Message))
          "prison-visit.changed" -> prisonVisitsService.updateVisit(objectMapper.readValue(sqsMessage.Message))
          else -> log.info("Received a message I wasn't expecting: {}", eventType)
        } else {
          log.warn("Feature switch is disabled for {}", eventType)
        }
      }

      "RETRY" -> {
        val context = objectMapper.readValue<VisitContext>(sqsMessage.Message)
        telemetryClient.trackEvent(
          "visit-retry-received",
          mapOf("id" to context.vsipId),
        )

        prisonVisitsService.createVisitRetry(context)

        telemetryClient.trackEvent(
          "visit-retry-success",
          mapOf("id" to context.vsipId),
        )
      }
    }
  }
}
