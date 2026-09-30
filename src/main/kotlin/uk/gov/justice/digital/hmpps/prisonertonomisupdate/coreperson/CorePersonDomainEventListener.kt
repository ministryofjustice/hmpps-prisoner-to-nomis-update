package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListenerNoMapping
import java.util.concurrent.CompletableFuture

@Service
class CorePersonDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  private val religionService: ReligionService,
  private val corePersonMergeService: CorePersonMergeService,
  telemetryClient: TelemetryClient,
) : DomainEventListenerNoMapping(
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "coreperson",
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("coreperson", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    val sqsMessage: SQSMessage = message.fromJson()
    val eventSource: EventSource? = sqsMessage.messageAttributes?.eventSource
    when (eventType) {
      "core-person-record.prison.religion.created" -> religionService.religionCreated(message.fromJson(), eventSource)
      "core-person-record.prison.record.merged" -> corePersonMergeService.mergePerson(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class SQSMessage(val messageAttributes: MessageAttributes? = null)

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class MessageAttributes(val eventSource: EventSource)

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class EventSource(val value: String, val type: String)

fun EventSource?.didOriginateInCpr() = this?.value != "NOMIS"
