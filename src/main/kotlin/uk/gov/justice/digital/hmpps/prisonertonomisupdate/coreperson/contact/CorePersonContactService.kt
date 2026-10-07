package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonRetryQueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.EventSource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact.CorePersonContactService.Companion.MappingTypes.CORE_PERSON_CONTACT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.didOriginateInCpr
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto.NomisContactType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOffenderPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.util.UUID

@Service
class CorePersonContactService(
  private val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val mappingApiService: CorePersonContactMappingApiService,
  private val corePersonRetryQueueService: CorePersonRetryQueueService,
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
    val entityName = CORE_PERSON_CONTACT.entityName

    val prisonNumber = event.personReference.identifiers.first { it.type == "prisonNumber" }.value
    val cprContactId = event.additionalInformation.cprContactId.toString()
    val telemetryMap = mutableMapOf(
      "prisonNumber" to prisonNumber,
      "cprContactId" to cprContactId,
    )

    if (!eventSource.didOriginateInCpr()) {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
      return
    }

    synchronise {
      name = entityName
      telemetryClient = this@CorePersonContactService.telemetryClient
      retryQueueService = corePersonRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        mappingApiService.getByCprContactIdOrNull(cprContactId)
      }
      transform {
        val cprContact = corePersonCprApiService.getPrisonerContact(prisonNumber, cprContactId)
        val contactValue = cprContact.value ?: throw IllegalStateException("Contact $cprContactId for $prisonNumber has no value")
        val rootOffenderId = corePersonNomisApiService.getRootOffenderId(prisonNumber)
        telemetryMap["rootOffenderId"] = rootOffenderId.toString()

        val (nomisContactType, nomisId) = if (cprContact.type == PrisonContact.Type.EMAIL) {
          NomisContactType.EMAIL to corePersonNomisApiService.createOffenderEmail(
            rootOffenderId,
            CreateOffenderEmailRequest(email = contactValue),
          ).emailAddressId
        } else {
          NomisContactType.PHONE to corePersonNomisApiService.createOffenderPhone(
            rootOffenderId,
            CreateOffenderPhoneRequest(
              number = contactValue,
              extension = cprContact.extension,
              typeCode = cprContact.type.toNomisPhoneType(),
            ),
          ).phoneId
        }
        telemetryMap["nomisContactType"] = nomisContactType.value
        telemetryMap["nomisId"] = nomisId.toString()

        CorePersonContactMappingDto(
          cprId = cprContactId,
          nomisId = nomisId,
          nomisContactType = nomisContactType,
          nomisPrisonNumber = prisonNumber,
          mappingType = CorePersonContactMappingDto.MappingType.CPR_CREATED,
        )
      }
      saveMapping { mappingApiService.createContactMapping(it) }
    }
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      CORE_PERSON_CONTACT -> createContactMapping(message.fromJson())
    }
  }

  suspend fun createContactMapping(message: CreateMappingRetryMessage<CorePersonContactMappingDto>) {
    mappingApiService.createContactMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${CORE_PERSON_CONTACT.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }

  suspend fun contactUpdated(event: ContactEvent, eventSource: EventSource?) {
    val entityName = CORE_PERSON_CONTACT.entityName

    val prisonNumber = event.personReference.identifiers.first { it.type == "prisonNumber" }.value
    val cprContactId = event.additionalInformation.cprContactId.toString()
    val telemetryMap = mutableMapOf(
      "prisonNumber" to prisonNumber,
      "cprContactId" to cprContactId,
    )

    if (!eventSource.didOriginateInCpr()) {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
      return
    }

    runCatching {
      val mapping = mappingApiService.getByCprContactId(cprContactId).also {
        telemetryMap["nomisContactType"] = it.nomisContactType.value
        telemetryMap["nomisId"] = it.nomisId.toString()
      }
      val cprContact = corePersonCprApiService.getPrisonerContact(prisonNumber, cprContactId)
      val contactValue = cprContact.value ?: throw IllegalStateException("Contact $cprContactId for $prisonNumber has no value")
      val cprContactType = if (cprContact.type == PrisonContact.Type.EMAIL) NomisContactType.EMAIL else NomisContactType.PHONE
      if (cprContactType != mapping.nomisContactType) {
        throw IllegalStateException("Contact $cprContactId for $prisonNumber has changed from ${mapping.nomisContactType} to $cprContactType")
      }
      val rootOffenderId = corePersonNomisApiService.getRootOffenderId(prisonNumber)
      telemetryMap["rootOffenderId"] = rootOffenderId.toString()

      when (mapping.nomisContactType) {
        NomisContactType.EMAIL -> corePersonNomisApiService.updateOffenderEmail(
          rootOffenderId,
          mapping.nomisId,
          UpdateOffenderEmailRequest(email = contactValue),
        )
        NomisContactType.PHONE -> corePersonNomisApiService.updateOffenderPhone(
          rootOffenderId,
          mapping.nomisId,
          UpdateOffenderPhoneRequest(
            number = contactValue,
            extension = cprContact.extension,
            typeCode = cprContact.type.toNomisPhoneType(),
          ),
        )
      }
    }.onSuccess {
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    }.onFailure { e ->
      telemetryClient.trackEvent("$entityName-update-failed", telemetryMap + ("reason" to (e.message ?: e.javaClass.name)))
      throw e
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

// NOMIS and CPR have slightly different phone type codes so need to translate
private fun PrisonContact.Type.toNomisPhoneType(): String = if (this == PrisonContact.Type.MOBILE) "MOB" else value

data class ContactEvent(
  val eventType: String,
  val additionalInformation: CprContactInfo,
  val personReference: PersonReferenceList,
)

data class CprContactInfo(
  val cprContactId: UUID,
)
