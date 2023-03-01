package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import java.util.concurrent.CompletableFuture

const val RETRY_CREATE_MAPPING = "RETRY_CREATE_MAPPING"

abstract class DomainEventListener(
  internal val service: SynchronisationService,
  internal val objectMapper: ObjectMapper,
  internal val eventFeatureSwitch: EventFeatureSwitch
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  internal fun onDomainEvent(
    rawMessage: String,
    processMessage: suspend (eventType: String, message: String) -> Unit
  ): CompletableFuture<Void> {
    log.debug("Received message {}", rawMessage)
    val sqsMessage: SQSMessage = rawMessage.fromJson()
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
          if (eventFeatureSwitch.isEnabled(eventType)) {
            processMessage(eventType, sqsMessage.Message)
          } else {
            log.warn("Feature switch is disabled for {}", eventType)
          }
        }

        RETRY_CREATE_MAPPING -> service.retryCreateMapping(sqsMessage.Message)
      }
    }
  }

  internal inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

private fun asCompletableFuture(
  process: suspend () -> Unit
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}
