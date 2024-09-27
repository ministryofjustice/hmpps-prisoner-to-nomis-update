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
    val dpsCaseNoteId = caseNoteEvent.additionalInformation.caseNoteUuid
    val offenderNo = requireNotNull(caseNoteEvent.personReference.findNomsNumber())
    val telemetryMap = mapOf(
      "dpsCaseNoteId" to dpsCaseNoteId,
      "offenderNo" to offenderNo,
    )

    if (caseNoteEvent.wasCreatedInDPS()) {
      synchronise {
        name = "caseNote"
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
    val dpsCaseNoteId = caseNoteEvent.additionalInformation.caseNoteUuid
    val offenderNo = requireNotNull(caseNoteEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsCaseNoteId" to dpsCaseNoteId,
      "offenderNo" to offenderNo,
    )

    if (caseNoteEvent.wasAmendedInDPS()) {
      runCatching {
        val mapping = mappingApiService.getOrNullByDpsId(dpsCaseNoteId)
          ?: throw IllegalStateException("Tried to amend an casenote that has never been created")
        telemetryMap["nomisBookingId"] = mapping.nomisBookingId.toString()
        telemetryMap["nomisCaseNoteId"] = mapping.nomisCaseNoteId.toString()

        val dpsCaseNote = dpsApiService.getCaseNote(offenderNo, dpsCaseNoteId)
        nomisApiService.updateCaseNote(
          caseNoteId = mapping.nomisCaseNoteId,
          dpsCaseNote.toNomisUpdateRequest(),
        )
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
    val dpsCaseNoteId = caseNoteEvent.additionalInformation.caseNoteUuid
    val offenderNo = requireNotNull(caseNoteEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsCaseNoteId" to dpsCaseNoteId,
      "offenderNo" to offenderNo,
    )
    if (caseNoteEvent.wasDeletedInDPS()) {
      runCatching {
        mappingApiService.getOrNullByDpsId(dpsCaseNoteId)?.also { mapping ->
          telemetryMap["nomisBookingId"] = mapping.nomisBookingId.toString()
          telemetryMap["nomisCaseNoteSequence"] = mapping.nomisCaseNoteId.toString()

          nomisApiService.deleteCaseNote(caseNoteId = mapping.nomisCaseNoteId)
          tryToDeletedMapping(dpsCaseNoteId)
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

  private suspend fun tryToDeletedMapping(dpsCaseNoteId: String) = kotlin.runCatching {
    mappingApiService.deleteByDpsId(dpsCaseNoteId)
  }.onFailure { e ->
    telemetryClient.trackEvent("casenotes-mapping-deleted-failed", mapOf("dpsCaseNoteId" to dpsCaseNoteId))
    log.warn("Unable to delete mapping for casenote $dpsCaseNoteId. Please delete manually", e)
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class CaseNoteEvent(
  val description: String?,
  val eventType: String,
  val additionalInformation: CaseNoteAdditionalInformation,
  val personReference: PersonReference,
)

data class CaseNoteAdditionalInformation(
  val caseNoteUuid: String,
  val source: CaseNoteSource,
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

enum class CaseNoteSource {
  DPS,
  NOMIS,
}

fun CaseNoteEvent.wasCreatedInDPS() = wasSourceDPS()
fun CaseNoteEvent.wasAmendedInDPS() = wasSourceDPS()
fun CaseNoteEvent.wasDeletedInDPS() = wasSourceDPS()
fun CaseNoteEvent.wasSourceDPS() = this.additionalInformation.source == CaseNoteSource.DPS
