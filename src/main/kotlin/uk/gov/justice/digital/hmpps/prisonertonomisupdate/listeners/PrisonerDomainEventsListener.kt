package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.IncentivesService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.PrisonVisitsService

@Service
class PrisonerDomainEventsListener(
  private val prisonVisitsService: PrisonVisitsService,
  private val incentivesService: IncentivesService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("prisoner", factory = "hmppsQueueContainerFactoryProxy", maxMessagesPerPoll = "1", maxInflightMessagesPerQueue = "1")
  fun onPrisonerChange(message: String) {
    log.debug("Received message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    when (sqsMessage.Type) {
      "Notification" -> {
        val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
        if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
          "prison-visit.booked" -> prisonVisitsService.createVisit(objectMapper.readValue(sqsMessage.Message))
          "prison-visit.cancelled" -> prisonVisitsService.cancelVisit(objectMapper.readValue(sqsMessage.Message))
          "prison-visit.changed" -> prisonVisitsService.updateVisit(objectMapper.readValue(sqsMessage.Message))
          "incentives.iep-review.inserted" -> incentivesService.createIncentive(objectMapper.readValue(sqsMessage.Message))
          else -> log.info("Received a message I wasn't expecting: {}", eventType)
        } else {
          log.warn("Feature switch is disabled for {}", eventType)
        }
      }
    }
  }
}
