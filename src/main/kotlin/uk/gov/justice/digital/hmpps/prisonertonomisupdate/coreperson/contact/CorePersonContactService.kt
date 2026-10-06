package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.EventSource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import java.util.UUID

@Service
class CorePersonContactService(
  private val telemetryClient: TelemetryClient,
  private val mappingApiService: CorePersonContactMappingApiService,
  private val jsonMapper: JsonMapper,
) : CreateMappingRetryable {
  companion object {
    enum class MappingTypes(val entityName: String) {
      CORE_PERSON_CONTACT("core-person-contact"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName } ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun contactCreated(event: ContactEvent, eventSource: EventSource?) {
    log.info(
      "Received {} event for prisoner {} with cprContactId {} from source {}",
      event.eventType,
      event.personReference.identifiers.firstOrNull { it.type == "prisonNumber" }?.value,
      event.additionalInformation.cprContactId,
      eventSource?.value,
    )
  }

  suspend fun contactUpdated(event: ContactEvent, eventSource: EventSource?) {
    log.info(
      "Received {} event for prisoner {} with cprContactId {} from source {}",
      event.eventType,
      event.personReference.identifiers.firstOrNull { it.type == "prisonNumber" }?.value,
      event.additionalInformation.cprContactId,
      eventSource?.value,
    )
  }

  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}

data class ContactEvent(
  val eventType: String,
  val additionalInformation: CprContactInfo,
  val personReference: PersonReferenceList,
)

data class CprContactInfo(
  val cprContactId: UUID,
)
