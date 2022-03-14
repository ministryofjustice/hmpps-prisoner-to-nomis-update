package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.VisitContext
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.PrisonVisitsService

@Service
class PrisonerDomainEventsListener(
  private val prisonVisitsService: PrisonVisitsService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "prisoner", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onPrisonerChange(message: String) {
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    log.debug("Received message {}", sqsMessage.MessageId)
    when (sqsMessage.Type) {
      "Notification" -> {
        val (eventType, prisonerId) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
        telemetryClient.trackEvent(
          "prisoner-domain-event-received",
          mapOf("eventType" to eventType, "offenderNo" to prisonerId),
        )
        if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
          "prison-visit.booked" -> prisonVisitsService.createVisit(objectMapper.readValue(sqsMessage.Message))
          "prison-visit.cancelled" -> prisonVisitsService.cancelVisit(objectMapper.readValue(sqsMessage.Message))
          else -> log.info("Received a message I wasn't expecting {}", eventType)
        } else {
          log.warn("Feature switch is disabled for {}", eventType)
        }
      }
      "RETRY" -> {
        val context = objectMapper.readValue<VisitContext>(sqsMessage.Message)
        telemetryClient.trackEvent(
          "prisoner-retry-received",
          mapOf("vsipId" to context.vsipId, "nomisId" to context.nomisId),
        )
        prisonVisitsService.createVisitRetry(context)
      }
    }
  }

  data class HMPPSDomainEvent(
    val eventType: String,
    val prisonerId: String
  )

  @JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
  data class SQSMessage(val Type: String, val Message: String, val MessageId: String)
}
