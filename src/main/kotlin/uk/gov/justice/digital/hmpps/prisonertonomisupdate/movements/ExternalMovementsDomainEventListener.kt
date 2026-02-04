package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Service
class ExternalMovementsDomainEventListener(
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  val externalMovementsService: ExternalMovementsService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = externalMovementsService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "externalmovements",
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Suppress("LoggingSimilarMessage")
  @SqsListener("externalmovements", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(
    rawMessage: String,
  ): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    log.info("Received message: {}", eventType)
    when (eventType) {
      "person.temporary-absence-authorisation.pending",
      "person.temporary-absence-authorisation.approved",
      "person.temporary-absence-authorisation.denied",
      "person.temporary-absence-authorisation.cancelled",
      "person.temporary-absence-authorisation.expired",
      "person.temporary-absence-authorisation.recategorised",
      "person.temporary-absence-authorisation.date-range-changed",
      "person.temporary-absence-authorisation.accompaniment-changed",
      "person.temporary-absence-authorisation.comments-changed",
      "person.temporary-absence-authorisation.transport-changed",
      "person.temporary-absence-authorisation.deferred",
      "person.temporary-absence-authorisation.relocated",
      -> externalMovementsService.authorisationChanged(message.fromJson())

      "person.temporary-absence.scheduled",
      "person.temporary-absence.denied",
      "person.temporary-absence.cancelled",
      "person.temporary-absence.started",
      "person.temporary-absence.completed",
      "person.temporary-absence.overdue",
      "person.temporary-absence.expired",
      "person.temporary-absence.recategorised",
      "person.temporary-absence.rescheduled",
      "person.temporary-absence.relocated",
      "person.temporary-absence.accompaniment-changed",
      "person.temporary-absence.transport-changed",
      "person.temporary-absence.comments-changed",
      -> externalMovementsService.occurrenceChanged(message.fromJson())

      // TODO external movements domain events with source=DPS are not published yet - these are placeholders
      "external-movements-api.temporary-absence-external-movement-out.created" -> externalMovementsService.externalMovementOutCreated(message.fromJson())
      "external-movements-api.temporary-absence-external-movement-in.created" -> externalMovementsService.externalMovementInCreated(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

data class TemporaryAbsenceEvent(
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: TemporaryAbsenceAdditionalInformation,
)

data class TemporaryAbsenceAdditionalInformation(
  val id: UUID,
  val source: String,
)

data class TemporaryAbsenceExternalMovementOutEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: ExternalMovementOutAdditionalInformation,
)

// TODO External movements domain events are not published yet - these are placeholders - hopefully they'll be the same form at as TemporaryAbsenceEvent so we can delete below
data class ExternalMovementOutAdditionalInformation(
  val externalMovementOutId: UUID,
  val scheduledMovementOutId: UUID? = null,
  val authorisationId: UUID? = null,
  val source: String,
)

data class TemporaryAbsenceExternalMovementInEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: ExternalMovementInAdditionalInformation,
)

data class ExternalMovementInAdditionalInformation(
  val externalMovementInId: UUID,
  val scheduledMovementInId: UUID? = null,
  val authorisationId: UUID? = null,
  val source: String,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  fun prisonerNumber() = identifiers.find { it.type == "NOMS" }!!.value
  data class Identifier(val type: String, val value: String)
}
