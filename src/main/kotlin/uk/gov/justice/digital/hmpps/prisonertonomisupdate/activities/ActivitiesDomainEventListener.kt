package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

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
class ActivitiesDomainEventListener(
  private val activitiesService: ActivitiesService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("activity", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-hmpps_prisoner_to_nomis_activity_queue", kind = SpanKind.SERVER)
  fun onChange(message: String) {
    log.debug("Received activity message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    when (sqsMessage.Type) {
      "Notification" -> {
        val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
        if (eventFeatureSwitch.isEnabled(eventType)) {
          when (eventType) {
            "activities.activity-schedule.created" -> activitiesService.createActivity(objectMapper.readValue(sqsMessage.Message))
            "activities.activity-schedule.amended" -> activitiesService.updateActivity(objectMapper.readValue(sqsMessage.Message))
            "activities.prisoner.allocated" -> activitiesService.createAllocation(objectMapper.readValue(sqsMessage.Message))
            "activities.prisoner.deallocated" -> activitiesService.deallocate(objectMapper.readValue(sqsMessage.Message))
            else -> log.info("Received a message I wasn't expecting: {}", eventType)
          }
        } else {
          log.warn("Feature switch is disabled for {}", eventType)
        }
      }

      "RETRY" -> {
        val context = objectMapper.readValue<ActivityContext>(sqsMessage.Message)
        telemetryClient.trackEvent(
          "activity-retry-received",
          mapOf("activityScheduleId" to context.activityScheduleId.toString()),
        )

        activitiesService.createRetry(context)

        telemetryClient.trackEvent(
          "activity-retry-success",
          mapOf("activityScheduleId" to context.activityScheduleId.toString()),
        )
      }
    }
  }
}
