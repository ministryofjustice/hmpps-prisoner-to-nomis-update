package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

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
class IncentivesDomainEventListener(
  private val incentivesService: IncentivesService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("incentive", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-hmpps_prisoner_to_nomis_incentive_queue", kind = SpanKind.SERVER)
  fun onPrisonerChange(message: String) {
    log.debug("Received incentive message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    when (sqsMessage.Type) {
      "Notification" -> {
        val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
        if (eventFeatureSwitch.isEnabled(eventType)) {
          when (eventType) {
            "incentives.iep-review.inserted" -> incentivesService.createIncentive(objectMapper.readValue(sqsMessage.Message))
            else -> log.info("Received a message I wasn't expecting: {}", eventType)
          }
        } else {
          log.warn("Feature switch is disabled for {}", eventType)
        }
      }

      "RETRY" -> {
        val context = objectMapper.readValue<IncentiveContext>(sqsMessage.Message)
        telemetryClient.trackEvent(
          "incentive-retry-received",
          mapOf("id" to context.incentiveId.toString()),
        )

        incentivesService.createIncentiveRetry(context)

        telemetryClient.trackEvent(
          "incentive-retry-success",
          mapOf("id" to context.incentiveId.toString()),
        )
      }
    }
  }
}
