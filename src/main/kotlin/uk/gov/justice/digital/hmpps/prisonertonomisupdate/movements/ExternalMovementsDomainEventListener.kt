package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Service
class ExternalMovementsDomainEventListener(
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  val externalMovementsService: ExternalMovementsService,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = externalMovementsService,
  objectMapper = objectMapper,
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
      "person.temporary-absence-authorisation.approved" -> externalMovementsService.authorisationApproved(message.fromJson())
      "person.temporary-absence.scheduled" -> externalMovementsService.occurrenceChanged(message.fromJson())
      "external-movements-api.temporary-absence-external-movement-out.created" -> externalMovementsService.externalMovementOutCreated(message.fromJson())
      "external-movements-api.temporary-absence-external-movement-in.created" -> externalMovementsService.externalMovementInCreated(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

data class TemporaryAbsenceAuthorisationEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: AuthorisationAdditionalInformation,
)

data class AuthorisationAdditionalInformation(
  val authorisationId: UUID,
  val source: String,
)

data class TapOccurrenceEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: TapOccurrenceAdditionalInformation,
)

data class TapOccurrenceAdditionalInformation(
  val occurrenceId: UUID,
  val source: String,
)

data class TemporaryAbsenceExternalMovementOutEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: ExternalMovementOutAdditionalInformation,
)

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
