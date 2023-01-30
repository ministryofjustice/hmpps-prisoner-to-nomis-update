package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.awspring.cloud.sqs.listener.Visibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import java.util.concurrent.CompletableFuture

@Service
class SentencingDomainEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val sentencingAdjustmentsService: SentencingAdjustmentsService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("sentencing", factory = "hmppsQueueContainerFactoryProxy")
  fun onPrisonerChange(
    message: String,
    visibility: Visibility
  ): CompletableFuture<Void> {
    log.debug("Received sentencing message {}", message)
    val sqsMessage: SQSMessage = message.fromJson()
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
          if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
            "sentencing.sentence.adjustment.created" ->
              sentencingAdjustmentsService.createAdjustment(sqsMessage.Message.fromJson())

            "sentencing.sentence.adjustment.updated",
            "sentencing.sentence.adjustment.delete" -> log.info("Received a valid sentencing {}", eventType)

            else -> log.info("Received a message I wasn't expecting: {}", eventType)
          } else {
            log.warn("Feature switch is disabled for {}", eventType)
          }
        }

        RETRY_CREATE_MAPPING ->
          sentencingAdjustmentsService.createSentencingAdjustmentMapping(sqsMessage.Message.fromJson())
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

private fun asCompletableFuture(
  process: suspend () -> Unit
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}
