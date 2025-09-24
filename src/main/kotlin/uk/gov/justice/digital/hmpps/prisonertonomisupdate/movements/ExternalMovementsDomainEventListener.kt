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
      "external-movements-api.temporary-absence-application.created" -> externalMovementsService.applicationCreated(message.fromJson())
      "external-movements-api.temporary-absence-outside-movement.created" -> externalMovementsService.outsideMovementCreated(message.fromJson())
      "external-movements-api.temporary-absence-scheduled-movement-out.created" -> externalMovementsService.scheduledMovementOutCreated(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}

data class TemporaryAbsenceApplicationEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: ApplicationAdditionalInformation,
)

data class ApplicationAdditionalInformation(
  val applicationId: UUID,
  val source: String,
)

data class TemporaryAbsenceOutsideMovementEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: OutsideMovementAdditionalInformation,
)

data class OutsideMovementAdditionalInformation(
  val outsideMovementId: UUID,
  val applicationId: UUID,
  val source: String,
)

data class TemporaryAbsenceScheduledMovementEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: ScheduledMovementAdditionalInformation,
)

data class ScheduledMovementAdditionalInformation(
  val scheduledMovementId: UUID,
  val applicationId: UUID,
  val source: String,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  fun prisonerNumber() = identifiers.find { it.type == "NOMS" }!!.value
  data class Identifier(val type: String, val value: String)
}
