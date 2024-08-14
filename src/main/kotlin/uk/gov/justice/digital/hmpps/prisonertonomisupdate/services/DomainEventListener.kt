package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
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

abstract class DomainEventListenerNoMapping(
  internal val objectMapper: ObjectMapper,
  internal val eventFeatureSwitch: EventFeatureSwitch,
  internal val telemetryClient: TelemetryClient,
) {
  fun onDomainEvent(
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
        else -> onNonDomainEvent(sqsMessage)
      }
    }
  }

  internal open fun onNonDomainEvent(sqsMessage: SQSMessage) = asCompletableFuture { }

  internal inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

abstract class DomainEventListener(
  internal val service: CreateMappingRetryable,
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
) : DomainEventListenerNoMapping(objectMapper, eventFeatureSwitch, telemetryClient) {

  override fun onNonDomainEvent(sqsMessage: SQSMessage): CompletableFuture<Void> =
    asCompletableFuture {
      when (sqsMessage.Type) {
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

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> = CoroutineScope(Dispatchers.Default).future {
  process()
}.thenAccept { }

interface CreateMappingRetryable {
  suspend fun retryCreateMapping(message: String)
}

data class MergeEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val additionalInformation: MergeAdditionalInformation,
)

data class MergeAdditionalInformation(
  @Schema(description = "enum - 'MERGE' indicates the prisoner records have been merged")
  val reason: String,
  @Schema(description = "NOMS number of the prisoner (this will be the value that the prisoner record now holds)")
  val nomsNumber: String,
  @Schema(description = "NOMS number that was MERGED into nomsNumber and then removed")
  val removedNomsNumber: String,
)

data class PersonReferenceList(
  val identifiers: List<PersonReference>,
)

data class PersonReference(
  val type: String,
  val value: String,
)
