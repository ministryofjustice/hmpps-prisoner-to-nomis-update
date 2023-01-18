package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage

@Service
class SentencingDomainEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "sentencing", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onPrisonerChange(message: String) {
    log.debug("Received sentencing message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    when (sqsMessage.Type) {
      "Notification" -> {
        val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
        if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
          "sentencing.sentence.adjustment.created",
          "sentencing.sentence.adjustment.updated",
          "sentencing.sentence.adjustment.delete" -> log.info("Received a valid sentencing {}", eventType)

          else -> log.info("Received a message I wasn't expecting: {}", eventType)
        } else {
          log.warn("Feature switch is disabled for {}", eventType)
        }
      }
    }
  }
}
