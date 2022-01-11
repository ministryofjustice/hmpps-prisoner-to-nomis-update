package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service

@Service
class HMPPSPrisonerDomainEventsListener(
  private val gson: Gson,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "prisoner", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onPrisonerChange(message: String) {
    val sqsMessage: SQSMessage = gson.fromJson(message, SQSMessage::class.java)
    log.info("Received message {}", sqsMessage.MessageId)
    val changeEvent: HMPPSDomainEvent = gson.fromJson(sqsMessage.Message, HMPPSDomainEvent::class.java)
    when (changeEvent.eventType) {
      "book-a-prison-visit.visit.created" -> log.info("Received ${changeEvent.eventType} event")
      "book-a-prison-visit.visit.amended" -> log.info("Received ${changeEvent.eventType} event")
      "book-a-prison-visit.visit.cancelled" -> log.info("Received ${changeEvent.eventType} event")
      else -> log.info("Received a message I wasn't expecting {}", changeEvent)
    }
  }

  data class HMPPSDomainEvent(
    val eventType: String,
  )

  data class SQSMessage(val Message: String, val MessageId: String)
}
