package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class TransferSchedulerDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  retryService: TransferSchedulerRetryService,
  private val scheduleService: TransferSchedulerScheduleService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = retryService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "transfermovements",
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Suppress("LoggingSimilarMessage")
  @SqsListener("transfermovements", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    log.info("Received message: {}", eventType)
    when (eventType) {
      "person.transfer.planned",
      "person.transfer.scheduled",
      "person.transfer.moved-to-planning",
      "person.transfer.cancelled",
      "person.transfer.expired",
      "person.transfer.in-transit",
      "person.transfer.completed",
      "person.transfer.rescheduled",
      "person.transfer.relocated",
      "person.transfer.recategorised",
      "person.transfer.logistics-changed",
      "person.transfer.reprioritised",
      "person.transfer.planning-comments-changed",
      "person.transfer.schedule-comments-changed",
      -> scheduleService.transferScheduleChanged(message.fromJson())

      "person.transfer.deleted",
      -> scheduleService.transferScheduleDeleted(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

data class TransferSchedulerEvent(
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: TransferSchedulerAdditionalInformation,
)

data class TransferSchedulerAdditionalInformation(
  val id: UUID,
  val source: String,
  val stage: String,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  fun prisonerNumber() = identifiers.find { it.type == "NOMS" }!!.value
  data class Identifier(val type: String, val value: String)
}
