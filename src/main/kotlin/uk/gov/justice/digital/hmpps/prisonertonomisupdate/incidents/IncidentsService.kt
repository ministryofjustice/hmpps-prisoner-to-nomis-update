package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem

@Service
class IncidentsService(
  private val telemetryClient: TelemetryClient,
) : CreateMappingRetryable {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun incidentCreated(event: IncidentEvent) {
    log.debug("Received incident created event for incident {}", event.additionalInformation.id)
    telemetryClient.trackEvent(
      "incidents-create-success",
      mapOf("incidentId" to event.additionalInformation.id),
    )
  }

  suspend fun incidentUpdated(event: IncidentEvent) {
    log.debug("Received incident updated event for incident {}", event.additionalInformation.id)
    telemetryClient.trackEvent(
      "incidents-update-success",
      mapOf("incidentId" to event.additionalInformation.id),
    )
  }

  suspend fun incidentDeleted(event: IncidentEvent) {
    log.debug("Received incident deleted event for incident {}", event.additionalInformation.id)
    telemetryClient.trackEvent(
      "incidents-delete-success",
      mapOf("incidentId" to event.additionalInformation.id),
    )
  }

  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}
data class IncidentEvent(
  val eventType: String,
  val description: String,
  val additionalInformation: IncidentAdditionalInformation,
)
data class IncidentAdditionalInformation(
  val id: String,
  val reportReference: Long,
  val source: CreatingSystem,
  // TODO - check - we may not need this
  val whatChanged: String?,
)

enum class IncidentSource {
  DPS,
  NOMIS,
}
