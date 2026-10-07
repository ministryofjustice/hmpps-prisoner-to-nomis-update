package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.didOriginateInCpr
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligionReadResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService.Companion.MappingTypes.CORE_PERSON_RELIGION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonInsertReligionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonMergeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonReligionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.util.*
import kotlin.collections.first
import kotlin.collections.orEmpty

@Service
class ReligionService(
  private val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val mapping: ReligionMappingApiService,
  private val corePersonRetryQueueService: CorePersonRetryQueueService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val jsonMapper: JsonMapper,
) : CreateMappingRetryable {
  companion object {
    enum class MappingTypes(val entityName: String) {
      CORE_PERSON_RELIGION("core-person-religion"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = MappingTypes.entries.find { it.entityName == entityName } ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun religionCreated(event: ReligionEvent, eventSource: EventSource?) {
    val entityName = CORE_PERSON_RELIGION.entityName

    val prisonNumber = event.personReference.identifiers.first { it.type == "prisonNumber" }.value
    val cprReligionId = event.additionalInformation.cprReligionId.toString()
    val telemetryMap = mutableMapOf(
      "prisonNumber" to prisonNumber,
      "cprReligionId" to cprReligionId,
    )

    if (eventSource.didOriginateInCpr()) {
      synchronise {
        name = entityName
        telemetryClient = this@ReligionService.telemetryClient
        retryQueueService = corePersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mapping.getReligionByCprIdOrNull(cprReligionId)
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
        saveMapping { mapping.createReligionMapping(it) }
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
    val mappings = mapping.getByCprIds(cprReligionIds)
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

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      CORE_PERSON_RELIGION -> createReligionMapping(message.fromJson())
    }
  }

  suspend fun createReligionMapping(message: CreateMappingRetryMessage<ReligionMappingDto>) {
    mapping.createReligionMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${CORE_PERSON_RELIGION.entityName}-create-success",
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

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

private fun PrisonReligionReadResponse.toNomisCreateRequest() = CorePersonInsertReligionRequest(
  beliefCode = religion.religionCode.value,
  startDate = religion.startDate,
  comments = religion.comments,
)
