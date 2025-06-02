package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem

@Service
class IncidentsService : CreateMappingRetryable {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun incidentCreated(event: IncidentEvent) = log.debug("Received incident created event for incident {}", event.additionalInformation.id)
  suspend fun incidentUpdated(event: IncidentEvent) = log.debug("Received incident updated event for incident {}", event.additionalInformation.id)

  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}
data class IncidentEvent(
  val description: String?,
  val eventType: String,
  val additionalInformation: IncidentAdditionalInformation,
  val personReference: PersonReference,
)
data class IncidentAdditionalInformation(
  val id: String,
  val source: CreatingSystem,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value
  fun findNomsNumber() = get(NOMS_NUMBER_TYPE)

  companion object {
    const val NOMS_NUMBER_TYPE = "NOMS"
    fun withNomsNumber(prisonNumber: String) = PersonReference(listOf(Identifier(NOMS_NUMBER_TYPE, prisonNumber)))
  }

  data class Identifier(val type: String, val value: String)
}
