package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.fasterxml.jackson.annotation.JsonProperty
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact.CorePersonContactService
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
  private val corePersonContactService: CorePersonContactService,
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
    val sqsMessage: SQSMessage = rawMessage.fromJson()
    val eventSource: EventSource? = sqsMessage.messageAttributes?.eventSource
    when (eventType) {
      "core-person-record.prison.religion.created" -> religionService.religionCreated(message.fromJson(), eventSource)
      "core-person-record.prison.record.merged" -> corePersonMergeService.mergePerson(message.fromJson())
      "core-person-record.prison.contact.created" -> corePersonContactService.contactCreated(message.fromJson(), eventSource)
      "core-person-record.prison.contact.updated" -> corePersonContactService.contactUpdated(message.fromJson(), eventSource)
      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

data class SQSMessage(
  @field:JsonProperty("MessageAttributes")
  val messageAttributes: MessageAttributes? = null,
)

// event source is actually lower case here
data class MessageAttributes(val eventSource: EventSource?)

data class EventSource(
  @field:JsonProperty("Value")
  val value: String,
  @field:JsonProperty("Type")
  val type: String,
)

fun EventSource?.didOriginateInCpr() = this?.value != "nomis"
