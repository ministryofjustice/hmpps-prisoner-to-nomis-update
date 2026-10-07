package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact.CorePersonContactMappingApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligionReadResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionMappingApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto.NomisContactType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonInsertReligionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonMergeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonReligionRequest
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
class CorePersonSynchronisationService(
  private val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val religionMappingApiService: ReligionMappingApiService,
  private val contactMappingApiService: CorePersonContactMappingApiService,
  private val corePersonRetryQueueService: CorePersonRetryQueueService,
  private val jsonMapper: JsonMapper,
) : CreateMappingRetryable {
  companion object {
    enum class MappingTypes(val entityName: String) {
      CORE_PERSON_RELIGION("core-person-religion"),
      CORE_PERSON_CONTACT("core-person-contact"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName }
          ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }
  }

  suspend fun religionCreated(event: ReligionEvent, eventSource: EventSource?) {
    val entityName = MappingTypes.CORE_PERSON_RELIGION.entityName

    val prisonNumber = event.personReference.identifiers.first { it.type == "prisonNumber" }.value
    val cprReligionId = event.additionalInformation.cprReligionId.toString()
    val telemetryMap = mutableMapOf(
      "prisonNumber" to prisonNumber,
      "cprReligionId" to cprReligionId,
    )

    if (eventSource.didOriginateInCpr()) {
      synchronise {
        name = entityName
        telemetryClient = this@CorePersonSynchronisationService.telemetryClient
        retryQueueService = corePersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          religionMappingApiService.getReligionByCprIdOrNull(cprReligionId)
        }
        transform {
          val religion = corePersonCprApiService.getReligion(prisonNumber, cprReligionId)

          val nomisBeliefId = corePersonNomisApiService.insertReligion(prisonNumber, religion.toNomisCreateRequest())
          telemetryMap["nomisBeliefId"] = nomisBeliefId.toString()
          ReligionMappingDto(
            cprId = cprReligionId,
            nomisId = nomisBeliefId,
            nomisPrisonNumber = prisonNumber,
            mappingType = ReligionMappingDto.MappingType.DPS_CREATED,
          )
        }
        saveMapping { religionMappingApiService.createReligionMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }

  suspend fun mergeReligions(toPrisonNumber: String) {
    val telemetryMap = mutableMapOf(
      "prisonNumber" to toPrisonNumber,
    )
    val toPerson = corePersonCprApiService.getCorePerson(toPrisonNumber)
    val cprReligions = toPerson?.religionHistory.orEmpty()
    val cprReligionIds = cprReligions.map { it.cprReligionId!! }
    val mappings = religionMappingApiService.getByCprIds(cprReligionIds)
    val missingMappings = cprReligionIds.toSet() - mappings.map { it.cprId }.toSet()
    if (missingMappings.isNotEmpty()) {
      throw IllegalStateException("Missing religion mappings for cpr religion ids: ${missingMappings.joinToString(", ")}")
    }
    val corePersonReligionRequests =
      mappings.map { outer ->
        CorePersonReligionRequest(outer.nomisId, cprReligions.first { it.cprReligionId == outer.cprId }.endDate)
      }
    if (corePersonReligionRequests.isNotEmpty()) {
      corePersonNomisApiService.mergeReligions(toPrisonNumber, CorePersonMergeRequest(corePersonReligionRequests))
    }
    telemetryClient.trackEvent("coreperson-religions-merged-success", telemetryMap)
  }

  suspend fun contactCreated(event: ContactEvent, eventSource: EventSource?) {
    val entityName = MappingTypes.CORE_PERSON_CONTACT.entityName

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
      telemetryClient = this@CorePersonSynchronisationService.telemetryClient
      retryQueueService = corePersonRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        contactMappingApiService.getByCprContactIdOrNull(cprContactId)
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
      saveMapping { contactMappingApiService.createContactMapping(it) }
    }
  }

  suspend fun contactUpdated(event: ContactEvent, eventSource: EventSource?) {
    val entityName = MappingTypes.CORE_PERSON_CONTACT.entityName

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
      val mapping = contactMappingApiService.getByCprContactId(cprContactId).also {
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

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      MappingTypes.CORE_PERSON_RELIGION -> createReligionMapping(message.fromJson())
      MappingTypes.CORE_PERSON_CONTACT -> createContactMapping(message.fromJson())
    }
  }

  suspend fun createReligionMapping(message: CreateMappingRetryMessage<ReligionMappingDto>) {
    religionMappingApiService.createReligionMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${MappingTypes.CORE_PERSON_RELIGION.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }

  suspend fun createContactMapping(message: CreateMappingRetryMessage<CorePersonContactMappingDto>) {
    contactMappingApiService.createContactMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${MappingTypes.CORE_PERSON_CONTACT.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }

  data class ReligionEvent(
    val eventType: String,
    val additionalInformation: CprReligionCreatedInfo,
    val personReference: PersonReferenceList,
  )

  data class CprReligionCreatedInfo(
    val cprReligionId: UUID,
  )

  data class ContactEvent(
    val eventType: String,
    val additionalInformation: CprContactInfo,
    val personReference: PersonReferenceList,
  )

  data class CprContactInfo(
    val cprContactId: UUID,
  )

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

private fun PrisonReligionReadResponse.toNomisCreateRequest() = CorePersonInsertReligionRequest(
  beliefCode = religion.religionCode.value,
  startDate = religion.startDate,
  comments = religion.comments,
)

// NOMIS and CPR have slightly different phone type codes so need to translate
private fun PrisonContact.Type.toNomisPhoneType(): String = if (this == PrisonContact.Type.MOBILE) "MOB" else value
