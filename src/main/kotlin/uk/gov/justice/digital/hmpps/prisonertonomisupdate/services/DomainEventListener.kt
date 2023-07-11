package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
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
  internal val service: CreateMappingRetryable,
  internal val objectMapper: ObjectMapper,
  internal val eventFeatureSwitch: EventFeatureSwitch,
  internal val telemetryClient: TelemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  internal fun onDomainEvent(
    rawMessage: String,
    processMessage: suspend (eventType: String, message: String) -> Unit,
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

        RETRY_CREATE_MAPPING -> runCatching { service.retryCreateMapping(sqsMessage.Message) }
          .onFailure {
            telemetryClient.trackEvent(
              "create-mapping-retry-failure",
              mapOf("retryMessage" to sqsMessage.Message),
              null,
            )
            throw it
          }
      }
    }
  }

  internal inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}

interface CreateMappingRetryable {
  suspend fun retryCreateMapping(message: String)
}
