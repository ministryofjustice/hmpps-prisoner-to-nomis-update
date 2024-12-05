package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.ZonedDateTime
import java.util.UUID

@Service
class CaseNotesService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: CaseNotesDpsApiService,
  private val nomisApiService: CaseNotesNomisApiService,
  private val mappingApiService: CaseNotesMappingApiService,
  private val caseNotesRetryQueueService: CaseNotesRetryQueueService,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun createCaseNote(caseNoteEvent: CaseNoteEvent) {
    val dpsCaseNoteId = caseNoteEvent.additionalInformation.id.toString()
    val offenderNo = requireNotNull(caseNoteEvent.personReference.findNomsNumber())
    val telemetryMap = mapOf(
      "dpsCaseNoteId" to dpsCaseNoteId,
      "offenderNo" to offenderNo,
    )

    if (caseNoteEvent.wasCreatedInDPS() && caseNoteEvent.notDpsOnly()) {
      synchronise {
        name = "casenotes"
        telemetryClient = this@CaseNotesService.telemetryClient
        retryQueueService = caseNotesRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getOrNullByDpsId(dpsCaseNoteId)
        }
        transform {
          dpsApiService.getCaseNote(offenderNo, dpsCaseNoteId)
            .let { dpsCaseNote ->
              nomisApiService.createCaseNote(offenderNo, dpsCaseNote.toNomisCreateRequest()).let { nomisCaseNote ->
                CaseNoteMappingDto(
                  dpsCaseNoteId = dpsCaseNoteId,
                  nomisBookingId = nomisCaseNote.bookingId,
                  nomisCaseNoteId = nomisCaseNote.id,
                  offenderNo = offenderNo,
                  mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
                )
              }
            }
        }
        saveMapping { mappingApiService.createMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("casenotes-create-ignored", telemetryMap)
    }
  }

  suspend fun createMapping(message: CreateMappingRetryMessage<CaseNoteMappingDto>) =
    mappingApiService.createMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "casenotes-create-success",
        message.telemetryAttributes,
        null,
      )
    }

  override suspend fun retryCreateMapping(message: String) = createMapping(message.fromJson())

  suspend fun updateCaseNote(caseNoteEvent: CaseNoteEvent) {
    val dpsCaseNoteId = caseNoteEvent.additionalInformation.id.toString()
    val offenderNo = requireNotNull(caseNoteEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsCaseNoteId" to dpsCaseNoteId,
      "offenderNo" to offenderNo,
    )

    if (caseNoteEvent.wasAmendedInDPS() && caseNoteEvent.notDpsOnly()) {
      runCatching {
        val mappings = mappingApiService.getOrNullByDpsId(dpsCaseNoteId)
          ?: throw IllegalStateException("Tried to amend an casenote that has no mapping")

        val dpsCaseNote = dpsApiService.getCaseNote(offenderNo, dpsCaseNoteId)
        val updateCaseNoteRequest = dpsCaseNote.toNomisUpdateRequest()
        var i = 1
        for (dto in mappings) {
          telemetryMap["nomisBookingId-$i"] = dto.nomisBookingId.toString()
          telemetryMap["nomisCaseNoteId-$i"] = dto.nomisCaseNoteId.toString()
          i++
          nomisApiService.updateCaseNote(dto.nomisCaseNoteId, updateCaseNoteRequest)
        }
        telemetryClient.trackEvent("casenotes-amend-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("casenotes-amend-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("casenotes-amend-ignored", telemetryMap)
    }
  }

  suspend fun deleteCaseNote(caseNoteEvent: CaseNoteEvent) {
    val dpsCaseNoteId = caseNoteEvent.additionalInformation.id.toString()
    val offenderNo = requireNotNull(caseNoteEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsCaseNoteId" to dpsCaseNoteId,
      "offenderNo" to offenderNo,
    )
    if (caseNoteEvent.wasDeletedInDPS() && caseNoteEvent.notDpsOnly()) {
      runCatching {
        mappingApiService.getOrNullByDpsId(dpsCaseNoteId)?.firstOrNull()?.also { mapping ->
          telemetryMap["nomisBookingId"] = mapping.nomisBookingId.toString()
          telemetryMap["nomisCaseNoteId"] = mapping.nomisCaseNoteId.toString()

          nomisApiService.deleteCaseNote(caseNoteId = mapping.nomisCaseNoteId)
          tryToDeleteMapping(dpsCaseNoteId)
          telemetryClient.trackEvent("casenotes-deleted-success", telemetryMap)
        } ?: also {
          telemetryClient.trackEvent("casenotes-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("casenotes-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("casenotes-deleted-ignored", telemetryMap)
    }
  }

  private suspend fun tryToDeleteMapping(dpsCaseNoteId: String) = kotlin.runCatching {
    mappingApiService.deleteByDpsId(dpsCaseNoteId)
  }.onFailure { e ->
    telemetryClient.trackEvent("casenotes-mapping-deleted-failed", mapOf("dpsCaseNoteId" to dpsCaseNoteId))
    log.warn("Unable to delete mapping for casenote $dpsCaseNoteId. Please delete manually", e)
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class CaseNoteEvent(
  val description: String,
  val eventType: String,
  val additionalInformation: CaseNoteAdditionalInformation,
  val personReference: PersonReference,

  val occurredAt: ZonedDateTime,
  val detailUrl: String,
  val version: Int = 1,
)

data class CaseNoteAdditionalInformation(
  val id: UUID,
  val legacyId: Long,
  val type: String,
  val subType: String,
  val source: CaseNoteSource,
  val syncToNomis: Boolean,
  val systemGenerated: Boolean,
)

data class PersonReference(val identifiers: Set<Identifier> = setOf()) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value
  fun findNomsNumber() = get(NOMS_NUMBER_TYPE)

  companion object {
    private const val NOMS_NUMBER_TYPE = "NOMS"
  }

  data class Identifier(val type: String, val value: String)
}

enum class CaseNoteSource {
  DPS,
  NOMIS,
}

fun CaseNoteEvent.wasCreatedInDPS() = wasSourceDPS()
fun CaseNoteEvent.wasAmendedInDPS() = wasSourceDPS()
fun CaseNoteEvent.wasDeletedInDPS() = wasSourceDPS()
fun CaseNoteEvent.wasSourceDPS() = this.additionalInformation.source == CaseNoteSource.DPS
fun CaseNoteEvent.notDpsOnly() = this.additionalInformation.syncToNomis
