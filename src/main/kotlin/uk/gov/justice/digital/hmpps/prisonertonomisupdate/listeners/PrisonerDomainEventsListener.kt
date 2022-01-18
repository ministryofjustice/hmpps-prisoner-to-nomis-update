package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.PrisonVisitsService
import java.time.OffsetDateTime

@Service
class PrisonerDomainEventsListener(
  private val prisonVisitsService: PrisonVisitsService,
  private val objectMapper: ObjectMapper
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "prisoner", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onPrisonerChange(message: String) {
    val sqsMessage: SQSMessage = objectMapper.readValue(message, SQSMessage::class.java)
    log.debug("Received message {}", sqsMessage.MessageId)
    val changeEvent: HMPPSDomainEvent = objectMapper.readValue(sqsMessage.Message, HMPPSDomainEvent::class.java)
    log.info("Received ${changeEvent.eventType} event")
    when (changeEvent.eventType) {
      "prison-visit.booked" -> prisonVisitsService.createVisit(
        visitId = changeEvent.additionalInformation!!.visitId!!,
        bookingDate = changeEvent.occurredAt.toLocalDateTime()
      )
      "prison-visit.revised" -> prisonVisitsService.updateVisit()
      "prison-visit.cancelled" -> prisonVisitsService.cancelVisit()
      else -> log.info("Received a message I wasn't expecting {}", changeEvent)
    }
  }

  data class HMPPSDomainEvent(
    val eventType: String,
    val additionalInformation: AdditionalInformation?,
    val occurredAt: OffsetDateTime
  )

  data class AdditionalInformation(
    val visitId: String?,
  )

  data class SQSMessage(val Message: String, val MessageId: String)
}
