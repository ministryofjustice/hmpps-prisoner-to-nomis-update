package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.PrisonVisitsService

@Service
class PrisonerDomainEventsListener(
  private val prisonVisitsService: PrisonVisitsService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "prisoner", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onPrisonerChange(message: String) {
    val sqsMessage: SQSMessage = objectMapper.readValue(message, SQSMessage::class.java)
    log.debug("Received message {}", sqsMessage.MessageId)
    val (eventType) = objectMapper.readValue(sqsMessage.Message, HMPPSDomainEvent::class.java)
    log.info("Received {} event", eventType)
    if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
      "prison-visit.booked" -> prisonVisitsService.createVisit(sqsMessage.Message.fromJson())
      "prison-visit.revised" -> prisonVisitsService.updateVisit()
      "prison-visit.cancelled" -> prisonVisitsService.cancelVisit()
      else -> log.info("Received a message I wasn't expecting {}", eventType)
    } else {
      log.warn("Feature switch is disabled for {}", eventType)
    }
  }

  data class HMPPSDomainEvent(
    val eventType: String,
  )

  data class SQSMessage(val Message: String, val MessageId: String)

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this, T::class.java)
}
