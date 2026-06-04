package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

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
class CourtSchedulerDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  retryService: CourtSchedulerRetryService,
  private val appearanceService: CourtSchedulerAppearanceService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = retryService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "courtmovements",
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Suppress("LoggingSimilarMessage")
  @SqsListener("courtmovements", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    log.info("Received message: {}", eventType)
    when (eventType) {
      "person.court-appearance.scheduled",
      "person.court-appearance.expired",
      "person.court-appearance.recorded",
      "person.court-appearance.relocated",
      "person.court-appearance.recategorised",
      "person.court-appearance.rescheduled",
      "person.court-appearance.comments-changed",
      -> appearanceService.courtAppearanceChanged(message.fromJson())

      "person.court-appearance.cancelled",
      -> appearanceService.courtAppearanceDeleted(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

data class CourtSchedulerEvent(
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: CourtSchedulerAdditionalInformation,
)

data class CourtSchedulerAdditionalInformation(
  val id: UUID,
  val source: String,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  fun prisonerNumber() = identifiers.find { it.type == "NOMS" }!!.value
  data class Identifier(val type: String, val value: String)
}
