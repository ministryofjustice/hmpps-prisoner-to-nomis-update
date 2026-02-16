package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyRecall
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacySearchSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacySentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceRecallMappingsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseBatchUpdateAndCreateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeNomisIdDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtSentenceIdPair
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtSentenceTermIdPair
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceTermId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceTermMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SimpleCourtSentencingIdPair
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtCaseCloneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifier
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ConvertToRecallRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ConvertToRecallResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtAppearanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtCaseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.DeleteRecallRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RecallRelatedSentenceDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ReturnToCustodyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RevertRecallRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceTermRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateRecallRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.QueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.collections.plus

typealias MappingSentenceId = uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceId

private const val DPS_VIDEO_LINK = "1da09b6e-55cb-4838-a157-ee6944f2094c"

@Service
class CourtSentencingService(
  private val courtSentencingApiService: CourtSentencingApiService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val courtCaseMappingService: CourtSentencingMappingService,
  private val courtSentencingRetryQueueService: CourtSentencingRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
  private val queueService: QueueService,
) : CreateMappingRetryable {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  enum class EntityType(val displayName: String) {
    COURT_CASE("court-case"),
    COURT_APPEARANCE("court-appearance"),
    COURT_APPEARANCE_RECALL("court-appearance-recall"),
    COURT_CHARGE("charge"),
    SENTENCE("sentence"),
    SENTENCE_TERM("sentence-term"),
    COURT_CASE_CLONE("court-case-clone"),
  }

  suspend fun createCourtCase(createEvent: CourtCaseCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val source = createEvent.additionalInformation.source
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(source)) {
      synchronise {
        name = EntityType.COURT_CASE.displayName
        telemetryClient = this@CourtSentencingService.telemetryClient
        retryQueueService = courtSentencingRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(courtCaseId)
        }

        transform {
          val courtCase = courtSentencingApiService.getCourtCase(courtCaseId)
          telemetryMap["offenderNo"] = offenderNo
          val nomisResponse =
            nomisApiService.createCourtCase(offenderNo, courtCase.toNomisCourtCase())

          queueService.sendMessageTrackOnFailure(
            queueId = "fromnomiscourtsentencing",
            eventType = "courtsentencing.resync.case",
            message = OffenderCaseResynchronisationEvent(
              offenderNo = offenderNo,
              caseId = nomisResponse.id,
              dpsCaseUuid = courtCaseId,
            ),
          )

          CourtCaseAllMappingDto(
            nomisCourtCaseId = nomisResponse.id,
            dpsCourtCaseId = courtCaseId,
            courtCharges = emptyList(),
            courtAppearances = emptyList(),
            sentences = emptyList(),
          )
        }
        saveMapping { courtCaseMappingService.createMapping(it) }
      }
    } else {
      telemetryMap["reason"] = "Court case created in NOMIS"
      telemetryClient.trackEvent(
        "court-case-create-ignored",
        telemetryMap,
        null,
      )
    }
  }

  private fun isDpsCreated(source: String) = source != CreatingSystem.NOMIS.name

  // also includes adding any charges that are associated with the appearance
  suspend fun createCourtAppearance(createEvent: CourtAppearanceCreatedEvent) {
    val dpsCourtCaseId = createEvent.additionalInformation.courtCaseId
    val source = createEvent.additionalInformation.source
    val dpsCourtAppearanceId = createEvent.additionalInformation.courtAppearanceId
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCourtCaseId,
      "dpsCourtAppearanceId" to dpsCourtAppearanceId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(source)) {
      synchronise {
        name = EntityType.COURT_APPEARANCE.displayName
        telemetryClient = this@CourtSentencingService.telemetryClient
        retryQueueService = courtSentencingRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          courtCaseMappingService.getMappingGivenCourtAppearanceIdOrNull(dpsCourtAppearanceId)
        }

        transform {
          val courtCaseMapping = courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = dpsCourtCaseId)
            ?.also {
              telemetryMap["nomisCourtCaseId"] = it.nomisCourtCaseId.toString()
            }
            ?: let {
              telemetryMap["reason"] = "Parent entity not found"
              throw ParentEntityNotFoundRetry(
                "Attempt to create a court appearance on a dps court case without a nomis mapping. Dps court case id: $dpsCourtCaseId not found for DPS court appearance $dpsCourtAppearanceId",
              )
            }

          val courtAppearance = courtSentencingApiService.getCourtAppearance(dpsCourtAppearanceId)
          courtAppearance.nomisOutcomeCode.let { telemetryMap["nomisOutcomeCode"] = it.toString() }
          telemetryMap["nextAppearanceDate"] = courtAppearance.nextCourtAppearance?.appearanceDate.asStringOrBlank()

          val courtEventChargesToUpdate: MutableList<Long> = mutableListOf()
          courtAppearance.charges.forEach { charge ->
            courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(charge.lifetimeUuid.toString())?.let { mapping ->
              courtEventChargesToUpdate.add(mapping.nomisCourtChargeId)
            } ?: let {
              telemetryMap["reason"] = "Parent entity not found"
              throw ParentEntityNotFoundRetry(
                "Attempt to associate a charge without a nomis charge mapping. Dps charge id: ${charge.lifetimeUuid} not found for DPS court appearance $dpsCourtAppearanceId",
              )
            }
          }

          nomisApiService.createCourtAppearance(
            offenderNo,
            courtCaseMapping.nomisCourtCaseId,
            courtAppearance.toNomisCourtAppearance(
              courtEventCharges = courtEventChargesToUpdate.map { it }
                .also { telemetryMap["courtEventCharges"] = it.toString() },
            ),
          ).also { response ->
            telemetryMap["nomisCourtAppearanceId"] = response.id.toString()
          }.toCourtCaseBatchMappingDto(dpsCourtAppearanceId = dpsCourtAppearanceId, offenderNo = offenderNo)
        }
        saveMapping { createAppearanceMappingsAndNotifyClonedCases(mappingsWrapper = it, offenderNo = offenderNo, telemetry = telemetryMap) }
      }
    } else {
      telemetryMap["reason"] = "Court appearance created in NOMIS"
      telemetryClient.trackEvent(
        "court-appearance-create-ignored",
        telemetryMap,
        null,
      )
    }
  }

  private suspend fun CreateCourtAppearanceResponse.toCourtCaseBatchMappingDto(dpsCourtAppearanceId: String, offenderNo: String): CourtCaseBatchUpdateAndCreateMappingsWrapper = CourtCaseBatchUpdateAndCreateMappingsWrapper(
    mappings =
    CourtCaseBatchUpdateAndCreateMappingDto(
      mappingsToCreate = CourtCaseBatchMappingDto(
        courtAppearances = listOf(
          CourtAppearanceMappingDto(
            dpsCourtAppearanceId = dpsCourtAppearanceId,
            nomisCourtAppearanceId = this.id,
          ),
        ),
        courtCases = emptyList(),
        courtCharges = emptyList(),
        sentences = emptyList(),
        sentenceTerms = emptyList(),
        mappingType = CourtCaseBatchMappingDto.MappingType.DPS_CREATED,
      ),
      mappingsToUpdate = CourtCaseBatchUpdateMappingDto(
        courtCases = clonedCourtCases.toCourtCases(),
        courtAppearances = clonedCourtCases.toCourtAppearances(),
        courtCharges = clonedCourtCases.toCourtCharges(),
        sentences = clonedCourtCases.toSentences(),
        sentenceTerms = clonedCourtCases.toSentenceTerms(),
      ),
    ),
    offenderNo = offenderNo,
    clonedClonedCourtCaseDetails = clonedCourtCases?.toClonedCourtCaseDetails(),
  )
  private suspend fun ConvertToRecallResponse.toCourtCaseBatchMappingDto(dpsRecallId: String, offenderNo: String): RecallAppearanceAndCreateMappingsWrapper = RecallAppearanceAndCreateMappingsWrapper(
    mappings = CourtAppearanceRecallMappingsDto(
      nomisCourtAppearanceIds = this.courtEventIds,
      dpsRecallId = dpsRecallId,
      mappingsToUpdate = CourtCaseBatchUpdateMappingDto(
        courtCases = clonedCourtCases.toCourtCases(),
        courtAppearances = clonedCourtCases.toCourtAppearances(),
        courtCharges = clonedCourtCases.toCourtCharges(),
        sentences = clonedCourtCases.toSentences(),
        sentenceTerms = clonedCourtCases.toSentenceTerms(),
      ),
    ),
    sentenceAdjustmentsActivated = this.sentenceAdjustmentsActivated,
    offenderNo = offenderNo,
    clonedClonedCourtCaseDetails = this.clonedCourtCases?.toClonedCourtCaseDetails(),
  )

  suspend fun deleteCourtCase(caseEvent: CourtCaseCreatedEvent) {
    val dpsCaseId = caseEvent.additionalInformation.courtCaseId
    val offenderNo = caseEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCaseId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(caseEvent.additionalInformation.source)) {
      runCatching {
        courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCaseId)?.also { mapping ->
          telemetryMap["nomisCourtCaseId"] = mapping.nomisCourtCaseId.toString()
          nomisApiService.deleteCourtCase(offenderNo = offenderNo, nomisCourtCaseId = mapping.nomisCourtCaseId)
          tryToDeleteMapping(dpsCaseId)
          telemetryClient.trackEvent("court-case-deleted-success", telemetryMap)
        } ?: also {
          telemetryClient.trackEvent("court-case-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("court-case-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("court-case-deleted-ignored", telemetryMap)
    }
  }

  suspend fun deleteCourtAppearance(createdEvent: CourtAppearanceCreatedEvent) {
    val dpsAppearanceId = createdEvent.additionalInformation.courtAppearanceId
    val dpsCaseId = createdEvent.additionalInformation.courtCaseId
    val offenderNo = createdEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCaseId,
      "dpsCourtAppearanceId" to dpsAppearanceId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(createdEvent.additionalInformation.source)) {
      runCatching {
        courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCaseId)?.also { mapping ->
          telemetryMap["nomisCourtCaseId"] = mapping.nomisCourtCaseId.toString()
          courtCaseMappingService.getMappingGivenCourtAppearanceIdOrNull(dpsAppearanceId)?.also { appearanceMapping ->
            telemetryMap["nomisCourtAppearanceId"] = appearanceMapping.nomisCourtAppearanceId.toString()
            nomisApiService.deleteCourtAppearance(
              offenderNo = offenderNo,
              nomisCourtCaseId = mapping.nomisCourtCaseId,
              nomisEventId = appearanceMapping.nomisCourtAppearanceId,
            )
            tryToDeleteCourtAppearanceMapping(dpsAppearanceId)
            telemetryClient.trackEvent("court-appearance-deleted-success", telemetryMap)
          } ?: also {
            telemetryClient.trackEvent("court-appearance-deleted-skipped", telemetryMap)
          }
        } ?: also {
          telemetryClient.trackEvent("court-appearance-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("court-appearance-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("court-appearance-deleted-ignored", telemetryMap)
    }
  }

  private suspend fun tryToDeleteMapping(dpsCaseId: String) = runCatching {
    courtCaseMappingService.deleteByDpsId(dpsCaseId)
  }.onFailure { e ->
    telemetryClient.trackEvent("court-case-mapping-deleted-failed", mapOf("dpsCourtCaseId" to dpsCaseId))
    log.warn("Unable to delete mapping for Court Case $dpsCaseId. Please delete manually", e)
  }
  private suspend fun tryToDeleteRecallMapping(dpsRecallId: String) = runCatching {
    courtCaseMappingService.deleteAppearanceRecallMappings(dpsRecallId)
  }.onFailure { e ->
    telemetryClient.trackEvent("recall-appearance-deleted-failed", mapOf("dpsRecallId" to dpsRecallId))
    log.warn("Unable to delete mapping for Recall $dpsRecallId. Please delete manually", e)
  }

  private suspend fun tryToDeleteCourtAppearanceMapping(dpsAppearanceId: String) = runCatching {
    courtCaseMappingService.deleteCourtAppearanceMappingByDpsId(dpsAppearanceId)
  }.onFailure { e ->
    telemetryClient.trackEvent(
      "court-appearance-mapping-deleted-failed",
      mapOf("dpsCourtAppearanceId" to dpsAppearanceId),
    )
    log.warn("Unable to delete mapping for Court Appearance $dpsAppearanceId. Please delete manually", e)
  }

  private suspend fun tryToDeleteSentenceMapping(offenderNo: String, dpsSentenceId: String) = runCatching {
    courtCaseMappingService.deleteSentenceMappingByDpsId(dpsSentenceId)
  }.onFailure { e ->
    telemetryClient.trackEvent(
      "sentence-mapping-deleted-failed",
      mapOf("dpsSentenceId" to dpsSentenceId, "offenderNo" to offenderNo),
    )
    log.warn("Unable to delete mapping for Sentence $dpsSentenceId for offender $offenderNo. Please delete manually", e)
  }

  private suspend fun tryToDeleteSentenceTermMapping(offenderNo: String, dpsTermId: String) = runCatching {
    courtCaseMappingService.deleteSentenceTermMappingByDpsId(dpsTermId)
  }.onFailure { e ->
    telemetryClient.trackEvent(
      "sentence-term-mapping-deleted-failed",
      mapOf("dpsTermId" to dpsTermId, "offenderNo" to offenderNo),
    )
    log.warn(
      "Unable to delete mapping for Sentence term $dpsTermId for offender $offenderNo. Please delete manually",
      e,
    )
  }

  suspend fun createCharge(createEvent: CourtChargeCreatedEvent) {
    val chargeId = createEvent.additionalInformation.courtChargeId
    val source = createEvent.additionalInformation.source
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsChargeId" to chargeId,
      "dpsCourtCaseId" to courtCaseId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(source)) {
      synchronise {
        name = EntityType.COURT_CHARGE.displayName
        telemetryClient = this@CourtSentencingService.telemetryClient
        retryQueueService = courtSentencingRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(chargeId)
        }

        transform {
          val courtCaseMapping =
            courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = courtCaseId)
              ?: let {
                telemetryMap["reason"] = "Parent entity not found"
                throw ParentEntityNotFoundRetry(
                  "Attempt to create a charge on a dps court case without a nomis Court Case mapping. Dps court case id: $courtCaseId not found for DPS charge $chargeId\"",
                )
              }

          val charge = courtSentencingApiService.getCourtCharge(chargeId)
          charge.nomisOutcomeCode.let { telemetryMap["nomisOutcomeCode"] = it.toString() }
          telemetryMap["nomisOffenceCode"] = charge.offenceCode

          val nomisChargeResponseDto =
            nomisApiService.createCourtCharge(
              offenderNo,
              courtCaseMapping.nomisCourtCaseId,
              charge.toNomisCourtCharge(),
            )

          CourtChargeMappingDto(
            nomisCourtChargeId = nomisChargeResponseDto.offenderChargeId,
            dpsCourtChargeId = charge.lifetimeUuid.toString(),
          ).also {
            telemetryMap["nomisChargeId"] = nomisChargeResponseDto.offenderChargeId.toString()
            telemetryMap["nomisCourtCaseId"] = courtCaseMapping.nomisCourtCaseId.toString()
          }
        }
        saveMapping { courtCaseMappingService.createChargeMapping(it) }
      }
    } else {
      telemetryMap["reason"] = "Charge created in NOMIS"
      telemetryClient.trackEvent(
        "charge-create-ignored",
        telemetryMap,
        null,
      )
    }
  }

  suspend fun updateCharge(createEvent: CourtChargeCreatedEvent) {
    val chargeId = createEvent.additionalInformation.courtChargeId
    val source = createEvent.additionalInformation.source
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val courtAppearanceId = createEvent.additionalInformation.courtAppearanceId!!
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsChargeId" to chargeId,
      "dpsCourtCaseId" to courtCaseId,
      "dpsCourtAppearanceId" to courtAppearanceId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(source)) {
      runCatching {
        val courtCaseMapping =
          courtCaseMappingService.getMappingGivenCourtCaseId(courtCaseId).also {
            telemetryMap["nomisCourtCaseId"] = it.nomisCourtCaseId.toString()
          }

        val appearanceMapping =
          courtCaseMappingService.getMappingGivenCourtAppearanceId(courtAppearanceId)
            .also {
              telemetryMap["nomisCourtAppearanceId"] = it.nomisCourtAppearanceId.toString()
            }

        val chargeMapping =
          courtCaseMappingService.getMappingGivenCourtChargeId(chargeId)
            .also {
              telemetryMap["nomisChargeId"] = it.nomisCourtChargeId.toString()
            }

        courtSentencingApiService.getCourtChargeByAppearance(appearanceId = courtAppearanceId, chargeId = chargeId).let { dpsCharge ->

          val nomisCourtCharge = dpsCharge.toNomisCourtCharge()
          nomisApiService.updateCourtCharge(
            offenderNo = offenderNo,
            nomisCourtCaseId = courtCaseMapping.nomisCourtCaseId,
            chargeId = chargeMapping.nomisCourtChargeId,
            nomisCourtAppearanceId = appearanceMapping.nomisCourtAppearanceId,
            request = nomisCourtCharge,
          ).also {
            nomisCourtCharge.resultCode1.let { telemetryMap["nomisOutcomeCode"] = it.toString() }
            telemetryMap["nomisOffenceCode"] = nomisCourtCharge.offenceCode
            telemetryClient.trackEvent(
              "charge-updated-success",
              telemetryMap,
            )
          }
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("charge-updated-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryMap["reason"] = "Charge updated in NOMIS"
      telemetryClient.trackEvent("charge-updated-ignored", telemetryMap, null)
    }
  }

  suspend fun updateCourtAppearance(createEvent: CourtAppearanceCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val source = createEvent.additionalInformation.source
    val courtAppearanceId = createEvent.additionalInformation.courtAppearanceId
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
      "dpsCourtAppearanceId" to courtAppearanceId,
      "offenderNo" to offenderNo,
    )

    if (isDpsCreated(source)) {
      runCatching {
        val courtCaseMapping =
          courtCaseMappingService.getMappingGivenCourtCaseId(courtCaseId).also {
            telemetryMap["nomisCourtCaseId"] = it.nomisCourtCaseId.toString()
          }
        val courtAppearanceMapping =
          courtCaseMappingService.getMappingGivenCourtAppearanceId(courtAppearanceId)
            .also {
              telemetryMap["nomisCourtAppearanceId"] = it.nomisCourtAppearanceId.toString()
            }

        val dpsCourtAppearance = courtSentencingApiService.getCourtAppearance(courtAppearanceId)

        val courtEventChargesToUpdate: MutableList<Long> = mutableListOf()

        dpsCourtAppearance.charges.forEach { charge ->
          courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(charge.lifetimeUuid.toString())?.let { mapping ->
            courtEventChargesToUpdate.add(mapping.nomisCourtChargeId)
          } ?: let {
            telemetryMap["reason"] = "Parent entity not found"
            throw ParentEntityNotFoundRetry(
              "Attempt to associate a charge without a nomis charge mapping. Dps charge id: ${charge.lifetimeUuid} not found for DPS court appearance $courtAppearanceId",
            )
          }
        }
        val nomisResponse = nomisApiService.updateCourtAppearance(
          offenderNo = offenderNo,
          nomisCourtCaseId = courtCaseMapping.nomisCourtCaseId,
          nomisCourtAppearanceId = courtAppearanceMapping.nomisCourtAppearanceId,
          request = dpsCourtAppearance.toNomisCourtAppearance(
            courtEventCharges = courtEventChargesToUpdate.map { it },
          ).also { telemetryMap["courtEventCharges"] = it.courtEventCharges.toString() },
        )

        CourtChargeBatchUpdateMappingDto(
          courtChargesToCreate = emptyList(),
          courtChargesToDelete = nomisResponse.deletedOffenderChargesIds.map {
            CourtChargeNomisIdDto(
              nomisCourtChargeId = it.offenderChargeId,
            )
          },
        ).takeIf { it.hasAnyMappingsToUpdate() }?.run {
          telemetryMap["deletedCourtChargeMappings"] =
            this.courtChargesToDelete.map { "nomisCourtChargeId: ${it.nomisCourtChargeId}" }.toString()
          createMapping(
            mapping = this,
            telemetryClient = telemetryClient,
            retryQueueService = courtSentencingRetryQueueService,
            eventTelemetry = telemetryMap,
            name = EntityType.COURT_CHARGE.displayName,
            postMapping = { courtCaseMappingService.createChargeBatchUpdateMapping(it) },
            log = log,
          )
        }
        dpsCourtAppearance.nomisOutcomeCode.let { telemetryMap["nomisOutcomeCode"] = it.toString() }

        telemetryClient.trackEvent(
          "court-appearance-updated-success",
          telemetryMap,
        )
      }.onFailure { e ->
        telemetryClient.trackEvent("court-appearance-updated-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryMap["reason"] = "Court appearance updated in NOMIS"
      telemetryClient.trackEvent("court-appearance-updated-ignored", telemetryMap, null)
    }
  }

  suspend fun createRetry(message: CreateMappingRetryMessage<CourtCaseAllMappingDto>) = courtCaseMappingService.createMapping(message.mapping).also {
    telemetryClient.trackEvent(
      "court-case-create-mapping-retry-success",
      message.telemetryAttributes,
      null,
    )
  }

  suspend fun createAppearanceMappingsAndNotifyClonedCases(mappingsWrapper: CourtCaseBatchUpdateAndCreateMappingsWrapper, offenderNo: String, telemetry: Map<String, String>) {
    createAndUpdateMappingsAndNotifyClonedCases(
      mappingsWrapper = mappingsWrapper,
      offenderNo = offenderNo,
    )

    mappingsWrapper.clonedClonedCourtCaseDetails?.also { details ->
      telemetryClient.trackEvent(
        "court-appearance-create-cases-cloned",
        telemetry + ("nomisCourtCaseIds" to details.clonedCourtCaseIds.joinToString()),
        null,
      )
    }
  }

  suspend fun createAppearanceMappingsRetry(message: CreateMappingRetryMessage<CourtCaseBatchUpdateAndCreateMappingsWrapper>) = with(message) {
    createAppearanceMappingsAndNotifyClonedCases(
      mappingsWrapper = mapping,
      offenderNo = mapping.offenderNo,
      telemetry = telemetryAttributes,
    ).also {
      telemetryClient.trackEvent(
        "court-appearance-create-mapping-retry-success",
        telemetryAttributes,
        null,
      )
    }
  }

  suspend fun tryCreateAppearanceMappingsAndNotifySentenceAdjustmentsAndClonedCases(
    mappingsWrapper: RecallAppearanceAndCreateMappingsWrapper,
    offenderNo: String,
    telemetry: Map<String, String>,
  ) {
    try {
      createAppearanceMappingsAndNotifySentenceAdjustmentsAndClonedCases(
        mappingsWrapper = mappingsWrapper,
        offenderNo = offenderNo,
        telemetry = telemetry,
      )
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "recall-mappings-inserted-failed",
        telemetry + ("reason" to (e.message ?: "unknown")),
        null,
      )
      courtSentencingRetryQueueService.sendMessage(
        mapping = mappingsWrapper,
        telemetryAttributes = telemetry,
        entityName = EntityType.COURT_APPEARANCE_RECALL.displayName,
      )
    }
  }

  suspend fun createAppearanceMappingsAndNotifySentenceAdjustmentsAndClonedCases(
    mappingsWrapper: RecallAppearanceAndCreateMappingsWrapper,
    offenderNo: String,
    telemetry: Map<String, String>,
  ) {
    courtCaseMappingService.createAppearanceRecallMappings(mappingsWrapper.mappings)

    // we might get adjustments that just need updating since the cases have not been cloned
    // or we might get cases that have been created and the adjustments have been created, in which case they will appear in both lists
    // or we might get both - so just create one set
    val sentenceAdjustmentsRequiringResync = (
      mappingsWrapper.clonedClonedCourtCaseDetails?.sentenceAdjustments
        ?: emptyList()
      ) + mappingsWrapper.sentenceAdjustmentsActivated.map {
      SentenceIdAndAdjustmentType(
        sentenceId = it.sentenceId,
        adjustmentIds = it.adjustmentIds.sorted(),
      )
    }

    sentenceAdjustmentsRequiringResync.toSet().forEach { adjustment ->
      adjustment.adjustmentIds.forEach { adjustmentId ->
        // since these are new adjustments send these individually given creating
        // a batch of adjustments is not idempotent if there are failures
        queueService.sendMessageTrackOnFailure(
          queueId = "fromnomiscourtsentencing",
          eventType = "courtsentencing.resync.sentence-adjustments",
          message = SyncSentenceAdjustment(
            offenderNo = offenderNo,
            sentences = listOf(
              SentenceIdAndAdjustmentIds(
                sentenceId = adjustment.sentenceId,
                adjustmentIds = listOf(adjustmentId),
              ),
            ),
          ),
        )
      }
    }

    mappingsWrapper.clonedClonedCourtCaseDetails?.also { details ->
      queueService.sendMessageTrackOnFailure(
        queueId = "fromnomiscourtsentencing",
        eventType = "courtsentencing.resync.case.booking",
        message = OffenderCaseBookingResynchronisationEvent(
          offenderNo = offenderNo,
          caseIds = details.clonedCourtCaseIds,
          fromBookingId = details.fromBookingId,
          toBookingId = details.toBookingId,
          casesMoved = details.casesMoved,
        ),
      )

      telemetryClient.trackEvent(
        "recall-create-cases-cloned-success",
        telemetry + ("nomisCourtCaseIds" to details.clonedCourtCaseIds.joinToString()),
        null,
      )
    }
  }

  suspend fun tryUpdateCaseMappingsAndNotifyClonedCases(mappingsWrapper: CourtCaseBatchUpdateAndCreateMappingsWrapper, offenderNo: String, telemetry: MutableMap<String, String>) {
    try {
      telemetry["nomisCourtCaseIds"] = mappingsWrapper.clonedClonedCourtCaseDetails!!.clonedCourtCaseIds.joinToString()
      updateCaseMappingsAndNotifyClonedCases(
        mappingsWrapper = mappingsWrapper,
        offenderNo = offenderNo,
        telemetry = telemetry,
      )
    } catch (e: Exception) {
      telemetryClient.trackEvent("court-case-cloned-repair-failed", telemetry + ("reason" to (e.message ?: "unknown")), null)
      courtSentencingRetryQueueService.sendMessage(
        mapping = mappingsWrapper,
        telemetryAttributes = telemetry,
        entityName = EntityType.COURT_CASE_CLONE.displayName,
      )
    }
  }

  suspend fun updateCaseMappingsAndNotifyClonedCases(mappingsWrapper: CourtCaseBatchUpdateAndCreateMappingsWrapper, offenderNo: String, telemetry: Map<String, String>) {
    createAndUpdateMappingsAndNotifyClonedCases(
      mappingsWrapper = mappingsWrapper,
      offenderNo = offenderNo,
    )
    telemetryClient.trackEvent(
      "court-case-cloned-repair",
      telemetry,
      null,
    )
  }
  suspend fun createAndUpdateMappingsAndNotifyClonedCases(mappingsWrapper: CourtCaseBatchUpdateAndCreateMappingsWrapper, offenderNo: String) {
    courtCaseMappingService.updateAndCreateMappings(mappingsWrapper.mappings)
    mappingsWrapper.clonedClonedCourtCaseDetails?.also { details ->
      queueService.sendMessageTrackOnFailure(
        queueId = "fromnomiscourtsentencing",
        eventType = "courtsentencing.resync.case.booking",
        message = OffenderCaseBookingResynchronisationEvent(
          offenderNo = offenderNo,
          caseIds = details.clonedCourtCaseIds,
          fromBookingId = details.fromBookingId,
          toBookingId = details.toBookingId,
          casesMoved = details.casesMoved,
        ),
      )

      details.sentenceAdjustments.forEach { adjustment ->
        adjustment.adjustmentIds.forEach { adjustmentId ->
          // since these are new adjustments send these individually given creating
          // a batch of adjustments is not idempotent if there are failures
          queueService.sendMessageTrackOnFailure(
            queueId = "fromnomiscourtsentencing",
            eventType = "courtsentencing.resync.sentence-adjustments",
            message = SyncSentenceAdjustment(
              offenderNo = offenderNo,
              sentences = listOf(
                SentenceIdAndAdjustmentIds(
                  sentenceId = adjustment.sentenceId,
                  adjustmentIds = listOf(adjustmentId),
                ),
              ),
            ),
          )
        }
      }
    }
  }

  suspend fun createMappingsAndNotifyClonedCasesRetry(message: CreateMappingRetryMessage<CourtCaseBatchUpdateAndCreateMappingsWrapper>) = with(message) {
    createAndUpdateMappingsAndNotifyClonedCases(
      mappingsWrapper = mapping,
      offenderNo = mapping.offenderNo,
    ).also {
      telemetryClient.trackEvent(
        "court-case-cloned-repair",
        telemetryAttributes,
        null,
      )
    }
  }

  suspend fun createAppearanceMappingsAndNotifySentenceAdjustmentsAndClonedCasesRetry(message: CreateMappingRetryMessage<RecallAppearanceAndCreateMappingsWrapper>) = with(message) {
    createAppearanceMappingsAndNotifySentenceAdjustmentsAndClonedCases(
      mappingsWrapper = mapping,
      offenderNo = mapping.offenderNo,
      telemetry = telemetryAttributes,
    ).also {
      telemetryClient.trackEvent(
        "court-appearance-recall-create-mapping-retry-success",
        telemetryAttributes,
        null,
      )
    }
  }

  suspend fun createChargeRetry(message: CreateMappingRetryMessage<CourtChargeMappingDto>) = courtCaseMappingService.createChargeMapping(message.mapping).also {
    telemetryClient.trackEvent(
      "charge-create-mapping-retry-success",
      message.telemetryAttributes,
      null,
    )
  }

  suspend fun createSentenceRetry(message: CreateMappingRetryMessage<SentenceMappingDto>) = courtCaseMappingService.createSentenceMapping(message.mapping).also {
    telemetryClient.trackEvent(
      "sentence-mapping-retry-success",
      message.telemetryAttributes,
      null,
    )
  }

  suspend fun createSentenceTermRetry(message: CreateMappingRetryMessage<SentenceTermMappingDto>) = courtCaseMappingService.createSentenceTermMapping(message.mapping).also {
    telemetryClient.trackEvent(
      "sentence-term-mapping-retry-success",
      message.telemetryAttributes,
      null,
    )
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (baseMapping.entityName) {
      EntityType.COURT_CASE.displayName -> createRetry(message.fromJson())
      EntityType.COURT_APPEARANCE.displayName -> createAppearanceMappingsRetry(message.fromJson())
      EntityType.COURT_APPEARANCE_RECALL.displayName -> createAppearanceMappingsAndNotifySentenceAdjustmentsAndClonedCasesRetry(message.fromJson())
      EntityType.COURT_CHARGE.displayName -> createChargeRetry(message.fromJson())
      EntityType.SENTENCE.displayName -> createSentenceRetry(message.fromJson())
      EntityType.SENTENCE_TERM.displayName -> createSentenceTermRetry(message.fromJson())
      EntityType.COURT_CASE_CLONE.displayName -> createMappingsAndNotifyClonedCasesRetry(message.fromJson())
      else -> throw IllegalArgumentException("Unknown entity type: ${baseMapping.entityName}")
    }
  }

  suspend fun createSentence(createEvent: SentenceCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val source = createEvent.additionalInformation.source
    val dpsSentenceId = createEvent.additionalInformation.sentenceId
    val dpsAppearanceId = createEvent.additionalInformation.courtAppearanceId
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
      "dpsSentenceId" to dpsSentenceId,
      "dpsCourtAppearanceId" to dpsAppearanceId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(source)) {
      synchronise {
        name = EntityType.SENTENCE.displayName
        telemetryClient = this@CourtSentencingService.telemetryClient
        retryQueueService = courtSentencingRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          courtCaseMappingService.getMappingGivenSentenceIdOrNull(dpsSentenceId)
        }

        transform {
          courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = courtCaseId)
            ?.let { courtCaseMapping ->
              telemetryMap["nomisCourtCaseId"] = courtCaseMapping.nomisCourtCaseId.toString()
              val dpsSentence =
                courtSentencingApiService.getSentence(dpsSentenceId)
                  .also { telemetryMap["dpsChargeId"] = it.chargeLifetimeUuid.toString() }

              courtCaseMappingService.getMappingGivenCourtAppearanceIdOrNull(dpsCourtAppearanceId = dpsAppearanceId)
                ?.let { appearanceMapping ->
                  telemetryMap["nomisCourtAppearanceId"] = appearanceMapping.nomisCourtAppearanceId.toString()
                  courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(dpsSentence.chargeLifetimeUuid.toString())
                    ?.let { chargeMapping ->

                      val consecutiveSentenceSeq =
                        getNomisConsecutiveSentenceSeqOrNull(dpsSentence, telemetryMap)
                      val nomisSentenceResponse =
                        nomisApiService.createSentence(
                          offenderNo,
                          request = dpsSentence.toNomisSentence(
                            nomisChargeId = chargeMapping.nomisCourtChargeId,
                            nomisConsecutiveToSentenceSeq = consecutiveSentenceSeq,
                            nomisEventId = appearanceMapping.nomisCourtAppearanceId,
                          ),
                          caseId = courtCaseMapping.nomisCourtCaseId,
                        )
                      queueService.sendMessageTrackOnFailure(
                        queueId = "fromnomiscourtsentencing",
                        eventType = "courtsentencing.resync.sentence",
                        message = OffenderSentenceResynchronisationEvent(
                          offenderNo = offenderNo,
                          dpsSentenceUuid = dpsSentence.lifetimeUuid.toString(),
                          dpsAppearanceUuid = createEvent.additionalInformation.courtAppearanceId,
                          dpsConsecutiveSentenceUuid = dpsSentence.consecutiveToLifetimeUuid?.toString(),
                          bookingId = nomisSentenceResponse.bookingId,
                          sentenceSeq = nomisSentenceResponse.sentenceSeq.toInt(),
                          caseId = courtCaseMapping.nomisCourtCaseId,
                        ),
                      )
                      telemetryMap["nomisSentenceSeq"] = nomisSentenceResponse.sentenceSeq.toString()
                      telemetryMap["nomisbookingId"] = nomisSentenceResponse.bookingId.toString()
                      telemetryMap["nomisChargeId"] = chargeMapping.nomisCourtChargeId.toString()
                      telemetryMap["nomisConsecutiveSentenceSequence"] = consecutiveSentenceSeq.toString()
                      telemetryMap["dpsConsecutiveSentenceUuid"] = consecutiveSentenceSeq.toString()

                      SentenceMappingDto(
                        nomisBookingId = nomisSentenceResponse.bookingId,
                        nomisSentenceSequence = nomisSentenceResponse.sentenceSeq.toInt(),
                        dpsSentenceId = dpsSentence.lifetimeUuid.toString(),
                      )
                    } ?: let {
                    telemetryMap["reason"] = "Parent entity not found"
                    throw ParentEntityNotFoundRetry(
                      "Attempt to associate a charge without a nomis charge mapping. Dps charge id: ${dpsSentence.chargeLifetimeUuid} not found for DPS Sentence $dpsSentenceId",
                    )
                  }
                } ?: let {
                telemetryMap["reason"] = "Parent entity not found"
                throw ParentEntityNotFoundRetry(
                  "Attempt to update a sentence associated with a DPS appearance without a nomis mapping. Dps appearance id: $dpsAppearanceId not found for DPS sentence $dpsSentenceId",
                )
              }
            } ?: let {
            telemetryMap["reason"] = "Parent entity not found"
            throw ParentEntityNotFoundRetry(
              "Attempt to create a sentence on a dps court case without a nomis mapping. Dps court case id: $courtCaseId not found for DPS sentence $dpsSentenceId",
            )
          }
        }
        saveMapping { courtCaseMappingService.createSentenceMapping(it) }
      }
    } else {
      telemetryMap["reason"] = "Sentence created in NOMIS"
      telemetryClient.trackEvent(
        "sentence-create-ignored",
        telemetryMap,
        null,
      )
    }
  }

  suspend fun createSentenceTerm(createEvent: PeriodLengthCreatedEvent) {
    val dpsCourtCaseId = createEvent.additionalInformation.courtCaseId
    val source = createEvent.additionalInformation.source
    val dpsSentenceId = createEvent.additionalInformation.sentenceId
    val dpsTermId = createEvent.additionalInformation.periodLengthId
    val dpsAppearanceId = createEvent.additionalInformation.courtAppearanceId
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCourtCaseId,
      "dpsSentenceId" to dpsSentenceId,
      "dpsCourtAppearanceId" to dpsAppearanceId,
      "dpsTermId" to dpsTermId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(source)) {
      synchronise {
        name = EntityType.SENTENCE_TERM.displayName
        telemetryClient = this@CourtSentencingService.telemetryClient
        retryQueueService = courtSentencingRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          courtCaseMappingService.getMappingGivenSentenceTermIdOrNull(dpsTermId)
        }

        transform {
          courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = dpsCourtCaseId)
            ?.let { caseMapping ->
              telemetryMap["nomisCourtCaseId"] = caseMapping.nomisCourtCaseId.toString()
              courtCaseMappingService.getMappingGivenSentenceIdOrNull(dpsSentenceId = dpsSentenceId)
                ?.let { sentenceMapping ->
                  telemetryMap["nomisSentenceSequence"] = sentenceMapping.nomisSentenceSequence.toString()
                  telemetryMap["nomisBookingId"] = sentenceMapping.nomisBookingId.toString()
                  val dpsPeriodLength =
                    courtSentencingApiService.getPeriodLength(dpsTermId)
                      .also { telemetryMap["dpsTermId"] = it.periodLengthUuid.toString() }
                  val nomisSentenceTermResponse =
                    nomisApiService.createSentenceTerm(
                      offenderNo,
                      request = dpsPeriodLength.toNomisSentenceTerm(),
                      caseId = caseMapping.nomisCourtCaseId,
                      sentenceSeq = sentenceMapping.nomisSentenceSequence,
                    )
                  telemetryMap["nomisSentenceSeq"] = nomisSentenceTermResponse.sentenceSeq.toString()
                  telemetryMap["nomisbookingId"] = nomisSentenceTermResponse.bookingId.toString()

                  SentenceTermMappingDto(
                    nomisBookingId = nomisSentenceTermResponse.bookingId,
                    nomisSentenceSequence = nomisSentenceTermResponse.sentenceSeq.toInt(),
                    nomisTermSequence = nomisSentenceTermResponse.termSeq.toInt(),
                    dpsTermId = dpsTermId,
                  )
                } ?: let {
                telemetryMap["reason"] = "Parent entity not found"
                throw ParentEntityNotFoundRetry(
                  "Attempt to create a sentence term without a sentence nomis mapping. Dps sentence id: $dpsSentenceId not found for DPS term $dpsTermId",
                )
              }
            } ?: let {
            telemetryMap["reason"] = "Parent entity not found"
            throw ParentEntityNotFoundRetry(
              "Attempt to create a sentence term without a case mapping. Dps case id: $dpsCourtCaseId not found for DPS term id $dpsTermId",
            )
          }
        }
        saveMapping { courtCaseMappingService.createSentenceTermMapping(it) }
      }
    } else {
      telemetryMap["reason"] = "Sentence term created in NOMIS"
      telemetryClient.trackEvent(
        "sentence-term-create-ignored",
        telemetryMap,
        null,
      )
    }
  }

  private suspend fun getNomisConsecutiveSentenceSeqOrNull(
    dpsSentence: LegacySentence,
    telemetryMap: MutableMap<String, String>,
  ): Long? = dpsSentence.consecutiveToLifetimeUuid?.let { consecutiveSentenceId ->
    telemetryMap["dpsConsecutiveSentenceId"] = consecutiveSentenceId.toString()
    courtCaseMappingService.getMappingGivenSentenceIdOrNull(consecutiveSentenceId.toString())?.nomisSentenceSequence?.toLong()
      ?: let {
        telemetryMap["reason"] = "Parent entity not found: consecutive sentence id: $consecutiveSentenceId"
        throw ParentEntityNotFoundRetry(
          "Attempt to make sentence consecutive to a sentence without a nomis mapping. Missing sentence id: $consecutiveSentenceId not found for DPS Sentence ${dpsSentence.lifetimeUuid}",
        )
      }
  }

  suspend fun updateSentence(createEvent: SentenceCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val source = createEvent.additionalInformation.source
    val sentenceId = createEvent.additionalInformation.sentenceId
    val courtAppearanceId = createEvent.additionalInformation.courtAppearanceId
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
      "dpsSentenceId" to sentenceId,
      "dpsCourtAppearanceId" to courtAppearanceId,
      "offenderNo" to offenderNo,
    )

    if (isDpsCreated(source)) {
      runCatching {
        val courtCaseMapping =
          courtCaseMappingService.getMappingGivenCourtCaseId(courtCaseId).also {
            telemetryMap["nomisCourtCaseId"] = it.nomisCourtCaseId.toString()
          }
        val appearanceMapping =
          courtCaseMappingService.getMappingGivenCourtAppearanceIdOrNull(courtAppearanceId) ?: let {
            telemetryMap["reason"] = "Parent entity not found. Dps appearance id: $courtAppearanceId"
            throw ParentEntityNotFoundRetry(
              "Attempt to associate an appearance without a nomis appearance mapping. Dps appearance id: $courtAppearanceId not found for DPS sentence $sentenceId",
            )
          }
        telemetryMap["nomisCourtAppearanceId"] = appearanceMapping.nomisCourtAppearanceId.toString()

        val sentenceMapping =
          courtCaseMappingService.getMappingGivenSentenceId(sentenceId)
            .also {
              telemetryMap["nomisSentenceSeq"] = it.nomisSentenceSequence.toString()
              telemetryMap["nomisBookingId"] = it.nomisBookingId.toString()
            }

        val dpsSentence = courtSentencingApiService.getSentence(sentenceId)

        val chargeMapping =
          courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(dpsSentence.chargeLifetimeUuid.toString()) ?: let {
            telemetryMap["reason"] = "Parent entity not found. Dps charge id: ${dpsSentence.chargeLifetimeUuid}"
            throw ParentEntityNotFoundRetry(
              "Attempt to associate a charge without a nomis charge mapping. Dps charge id: ${dpsSentence.chargeLifetimeUuid} not found for DPS sentence ${dpsSentence.lifetimeUuid}",
            )
          }

        val consecutiveSentenceSeq =
          getNomisConsecutiveSentenceSeqOrNull(dpsSentence, telemetryMap)
        nomisApiService.updateSentence(
          offenderNo = offenderNo,
          caseId = courtCaseMapping.nomisCourtCaseId,
          sentenceSeq = sentenceMapping.nomisSentenceSequence,
          request = dpsSentence.toNomisSentence(
            nomisChargeId = chargeMapping.nomisCourtChargeId,
            nomisConsecutiveToSentenceSeq = consecutiveSentenceSeq,
            nomisEventId = appearanceMapping.nomisCourtAppearanceId,
          ),
        )

        telemetryClient.trackEvent(
          "sentence-updated-success",
          telemetryMap,
        )
      }.onFailure { e ->
        telemetryClient.trackEvent("sentence-updated-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryMap["reason"] = "Sentence updated in NOMIS"
      telemetryClient.trackEvent("sentence-updated-ignored", telemetryMap, null)
    }
  }

  suspend fun createRecallSentences(recallInsertedEvent: RecallEvent) {
    val source = recallInsertedEvent.additionalInformation.source
    val recallId = recallInsertedEvent.additionalInformation.recallId
    val sentenceIds = recallInsertedEvent.additionalInformation.sentenceIds
    val offenderNo: String = recallInsertedEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsRecallId" to recallId,
      "dpsSentenceIds" to sentenceIds.joinToString(),
      "offenderNo" to offenderNo,
    )

    if (isDpsCreated(source) && sentenceIds.isNotEmpty()) {
      runCatching {
        val recall = courtSentencingApiService.getRecall(recallId).also {
          telemetryMap["recallType"] = it.recallType.name
        }
        val sentenceAndMappings = getSentenceAndMappings(sentenceIds).also { telemetryMap.toTelemetry(it) }

        val response = nomisApiService.recallSentences(
          offenderNo,
          ConvertToRecallRequest(
            sentences = sentenceAndMappings.map { sentence ->
              RecallRelatedSentenceDetails(
                sentenceId =
                SentenceId(
                  offenderBookingId = sentence.nomisBookingId,
                  sentenceSequence = sentence.nomisSentenceSequence,
                ),
                sentenceCategory = sentence.dpsSentence.sentenceCategory,
                sentenceCalcType = sentence.dpsSentence.sentenceCalcType,
                active = sentence.dpsSentence.active,
              )
            },
            returnToCustody = recall.takeIf { recall.recallType.isFixedTermRecall() && recall.hasReturnToCustodyDate() }?.let {
              ReturnToCustodyRequest(
                returnToCustodyDate = it.returnToCustodyDate ?: it.revocationDate!!,
                enteredByStaffUsername = recall.recallBy,
                recallLength = recall.recallType.toDays()!!,
              )
            },
            // should always be present for recalls from DPS
            recallRevocationDate = recall.revocationDate!!,
          ),
        )

        tryCreateAppearanceMappingsAndNotifySentenceAdjustmentsAndClonedCases(
          response.toCourtCaseBatchMappingDto(
            dpsRecallId = recallId,
            offenderNo = offenderNo,
          ),
          offenderNo = offenderNo,
          telemetry = telemetryMap,
        )

        telemetryClient.trackEvent(
          "recall-inserted-success",
          telemetryMap,
        )
      }.onFailure { e ->
        telemetryClient.trackEvent("recall-inserted-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryMap["reason"] = "Recall inserted in NOMIS"
      telemetryClient.trackEvent("recall-inserted-ignored", telemetryMap, null)
    }
  }

  suspend fun updateRecallSentences(recallUpdateEvent: UpdateRecallEvent) {
    val source = recallUpdateEvent.additionalInformation.source
    val recallId = recallUpdateEvent.additionalInformation.recallId
    val offenderNo: String = recallUpdateEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsRecallId" to recallId,
      "offenderNo" to offenderNo,
      "dpsSentenceIds" to recallUpdateEvent.additionalInformation.sentenceIds.joinToString(),
    )

    if (isDpsCreated(source)) {
      runCatching {
        val recall = courtSentencingApiService.getRecall(recallId).also {
          telemetryMap["recallType"] = it.recallType.name
        }
        val dpsSentenceIds = recall.sentenceIds.map { it.toString() }
        val dpsRemovedSentenceIds = recallUpdateEvent.additionalInformation.previousSentenceIds.subtract(dpsSentenceIds).toList()
        val sentenceAndMappings = getSentenceAndMappings(dpsSentenceIds).also { telemetryMap.toTelemetry(it) }
        val sentenceAndMappingsToRemove = getSentenceAndMappings(dpsRemovedSentenceIds).also { telemetryMap.toRemovedTelemetry(it) }
        val breachCourtAppearanceIds =
          courtCaseMappingService.getAppearanceRecallMappings(recallId).map { it.nomisCourtAppearanceId }
        nomisApiService.updateRecallSentences(
          offenderNo,
          UpdateRecallRequest(
            sentences = sentenceAndMappings.map { it.toRecallRelatedSentenceDetails() },
            sentencesRemoved = sentenceAndMappingsToRemove.map { it.toRecallRelatedSentenceDetails() },
            returnToCustody = recall.takeIf { recall.recallType.isFixedTermRecall() && recall.hasReturnToCustodyDate() }?.let {
              ReturnToCustodyRequest(
                returnToCustodyDate = it.returnToCustodyDate ?: it.revocationDate!!,
                enteredByStaffUsername = recall.recallBy,
                recallLength = recall.recallType.toDays()!!,
              )
            },
            // should always be present for recalls from DPS
            recallRevocationDate = recall.revocationDate!!,
            beachCourtEventIds = breachCourtAppearanceIds,
          ),
        )

        telemetryClient.trackEvent(
          "recall-updated-success",
          telemetryMap,
        )
      }.onFailure { e ->
        telemetryClient.trackEvent("recall-updated-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryMap["reason"] = "Recall updated in NOMIS"
      telemetryClient.trackEvent("recall-updated-ignored", telemetryMap, null)
    }
  }

  suspend fun deleteRecallSentences(recallDeletedEvent: RecallEvent) {
    val source = recallDeletedEvent.additionalInformation.source
    val recallId = recallDeletedEvent.additionalInformation.recallId
    val previousRecallId = recallDeletedEvent.additionalInformation.previousRecallId
    val offenderNo: String = recallDeletedEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsRecallId" to recallId,
      "offenderNo" to offenderNo,
      "dpsSentenceIds" to recallDeletedEvent.additionalInformation.sentenceIds.joinToString(),
    )

    if (isDpsCreated(source)) {
      runCatching {
        if (previousRecallId != null) {
          telemetryMap["dpsPreviousRecallId"] = previousRecallId
          val recall = courtSentencingApiService.getRecall(previousRecallId).also {
            telemetryMap["recallType"] = it.recallType.name
            telemetryMap["dpsSentenceIds"] = it.sentenceIds.joinToString()
            telemetryMap["dpsOriginalSentenceIds"] = recallDeletedEvent.additionalInformation.sentenceIds.joinToString()
          }
          val dpsSentenceIdsOnPreviousRecall = recall.sentenceIds.map { it.toString() }
          val dpsSentenceIds = (dpsSentenceIdsOnPreviousRecall + recallDeletedEvent.additionalInformation.sentenceIds).distinct()
          val sentenceAndMappings = getSentenceAndMappings(dpsSentenceIds).also { telemetryMap.toTelemetry(it) }
          val breachCourtAppearanceIds =
            courtCaseMappingService.getAppearanceRecallMappings(recallId).map { it.nomisCourtAppearanceId }
          nomisApiService.revertRecallSentences(
            offenderNo,
            RevertRecallRequest(
              sentences = sentenceAndMappings.map { sentence ->
                RecallRelatedSentenceDetails(
                  sentenceId =
                  SentenceId(
                    offenderBookingId = sentence.nomisBookingId,
                    sentenceSequence = sentence.nomisSentenceSequence,
                  ),
                  sentenceCategory = sentence.dpsSentence.sentenceCategory,
                  sentenceCalcType = sentence.dpsSentence.sentenceCalcType,
                  active = sentence.dpsSentence.active,
                )
              },
              returnToCustody = recall.takeIf { recall.recallType.isFixedTermRecall() && recall.hasReturnToCustodyDate() }?.let {
                ReturnToCustodyRequest(
                  returnToCustodyDate = it.returnToCustodyDate ?: it.revocationDate!!,
                  enteredByStaffUsername = recall.recallBy,
                  recallLength = recall.recallType.toDays()!!,
                )
              },
              beachCourtEventIds = breachCourtAppearanceIds,
            ),
          )
        } else {
          val dpsSentenceIds = recallDeletedEvent.additionalInformation.sentenceIds
          val sentenceAndMappings = getSentenceAndMappings(dpsSentenceIds).also { telemetryMap.toTelemetry(it) }
          val breachCourtAppearanceIds =
            courtCaseMappingService.getAppearanceRecallMappings(recallId).map { it.nomisCourtAppearanceId }
          nomisApiService.deleteRecallSentences(
            offenderNo,
            DeleteRecallRequest(
              sentences = sentenceAndMappings.map { sentence ->
                RecallRelatedSentenceDetails(
                  sentenceId =
                  SentenceId(
                    offenderBookingId = sentence.nomisBookingId,
                    sentenceSequence = sentence.nomisSentenceSequence,
                  ),
                  sentenceCategory = sentence.dpsSentence.sentenceCategory,
                  sentenceCalcType = sentence.dpsSentence.sentenceCalcType,
                  active = sentence.dpsSentence.active,
                )
              },
              beachCourtEventIds = breachCourtAppearanceIds,
            ),
          )
        }

        tryToDeleteRecallMapping(recallId)
        telemetryClient.trackEvent(
          "recall-deleted-success",
          telemetryMap,
        )
      }.onFailure { e ->
        telemetryClient.trackEvent("recall-deleted-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryMap["reason"] = "Recall deleted in NOMIS"
      telemetryClient.trackEvent("recall-deleted-ignored", telemetryMap, null)
    }
  }

  data class DpsSentenceWithNomisKey(
    val dpsSentence: LegacySentence,
    val nomisBookingId: Long,
    val nomisSentenceSequence: Long,
  )

  fun DpsSentenceWithNomisKey.toRecallRelatedSentenceDetails() = RecallRelatedSentenceDetails(
    sentenceId =
    SentenceId(
      offenderBookingId = this.nomisBookingId,
      sentenceSequence = this.nomisSentenceSequence,
    ),
    sentenceCategory = this.dpsSentence.sentenceCategory,
    sentenceCalcType = this.dpsSentence.sentenceCalcType,
    active = this.dpsSentence.active,
  )

  private suspend fun getSentenceAndMappings(dpsSentenceIds: List<String>): List<DpsSentenceWithNomisKey> = dpsSentenceIds.takeIf { it.isNotEmpty() }?.let {
    courtSentencingApiService.getSentences(LegacySearchSentence(dpsSentenceIds.map { UUID.fromString(it) }))
      .let { dpsSentences ->
        val mappings = courtCaseMappingService.getMappingsGivenSentenceIds(dpsSentenceIds)
        dpsSentences.map { sentence ->
          val mapping = mappings.find { it.dpsSentenceId == sentence.lifetimeUuid.toString() }
            ?: throw IllegalStateException("Not all sentences have a mapping: ${mappings.map { it.dpsSentenceId }}")
          DpsSentenceWithNomisKey(
            dpsSentence = sentence,
            nomisBookingId = mapping.nomisBookingId,
            nomisSentenceSequence = mapping.nomisSentenceSequence.toLong(),
          )
        }
      }
  } ?: emptyList()

  private fun MutableMap<String, String>.toTelemetry(sentence: List<DpsSentenceWithNomisKey>) {
    this["nomisSentenceSeq"] = sentence.joinToString { it.nomisSentenceSequence.toString() }
    this["nomisBookingId"] = sentence.map { it.nomisBookingId }.toSortedSet().joinToString()
    this["dpsSentenceTypes"] = sentence.map { it.dpsSentence.sentenceCalcType }.toSortedSet().joinToString()
  }
  private fun MutableMap<String, String>.toRemovedTelemetry(sentences: List<DpsSentenceWithNomisKey>) = this.takeIf { sentences.isNotEmpty() }?.apply {
    this["removedNomisSentenceSeq"] = sentences.joinToString { it.nomisSentenceSequence.toString() }
    this["removedNomisBookingId"] = sentences.map { it.nomisBookingId }.toSortedSet().joinToString()
    this["removedDpsSentenceTypes"] = sentences.map { it.dpsSentence.sentenceCalcType }.toSortedSet().joinToString()
    this["removedDpsSentenceIds"] = sentences.map { it.dpsSentence.lifetimeUuid }.toSortedSet().joinToString()
  }

  private fun LegacyRecall.RecallType.toDays(): Int? = when (this) {
    LegacyRecall.RecallType.LR -> null
    LegacyRecall.RecallType.FTR_14 -> 14
    LegacyRecall.RecallType.FTR_28 -> 28
    LegacyRecall.RecallType.FTR_HDC_14 -> 14
    LegacyRecall.RecallType.FTR_HDC_28 -> 28
    LegacyRecall.RecallType.CUR_HDC -> null
    LegacyRecall.RecallType.IN_HDC -> null
    LegacyRecall.RecallType.FTR_56 -> 56
  }

  private fun LegacyRecall.RecallType.isFixedTermRecall(): Boolean = when (this) {
    LegacyRecall.RecallType.LR -> false
    LegacyRecall.RecallType.FTR_14 -> true
    LegacyRecall.RecallType.FTR_28 -> true
    LegacyRecall.RecallType.FTR_HDC_14 -> true
    LegacyRecall.RecallType.FTR_HDC_28 -> true
    LegacyRecall.RecallType.CUR_HDC -> false
    LegacyRecall.RecallType.IN_HDC -> false
    LegacyRecall.RecallType.FTR_56 -> true
  }

  suspend fun updateSentenceTerm(createEvent: PeriodLengthCreatedEvent) {
    val courtCaseId = createEvent.additionalInformation.courtCaseId
    val source = createEvent.additionalInformation.source
    val sentenceId = createEvent.additionalInformation.sentenceId
    val termId = createEvent.additionalInformation.periodLengthId
    val courtAppearanceId = createEvent.additionalInformation.courtAppearanceId
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to courtCaseId,
      "dpsSentenceId" to sentenceId,
      "dpsTermId" to termId,
      "dpsCourtAppearanceId" to courtAppearanceId,
      "offenderNo" to offenderNo,
    )

    if (isDpsCreated(source)) {
      runCatching {
        val courtCaseMapping =
          courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(courtCaseId)?.also {
            telemetryMap["nomisCourtCaseId"] = it.nomisCourtCaseId.toString()
          } ?: let {
            telemetryMap["reason"] = "Parent entity not found. Dps term id: $termId"
            throw ParentEntityNotFoundRetry(
              "Attempt to update a sentence term without a nomis case mapping. Dps case id: $courtCaseId not found for DPS period length $termId",
            )
          }

        val sentenceTermMapping =
          courtCaseMappingService.getMappingGivenSentenceTermId(termId)
            .also {
              telemetryMap["nomisSentenceSeq"] = it.nomisSentenceSequence.toString()
              telemetryMap["nomisTermSeq"] = it.nomisTermSequence.toString()
              telemetryMap["nomisBookingId"] = it.nomisBookingId.toString()
            }

        val dpsPeriodLength = courtSentencingApiService.getPeriodLength(termId)

        nomisApiService.updateSentenceTerm(
          offenderNo = offenderNo,
          caseId = courtCaseMapping.nomisCourtCaseId,
          sentenceSeq = sentenceTermMapping.nomisSentenceSequence,
          termSeq = sentenceTermMapping.nomisTermSequence,
          request = dpsPeriodLength.toNomisSentenceTerm(),
        )

        telemetryClient.trackEvent(
          "sentence-term-updated-success",
          telemetryMap,
        )
      }.onFailure { e ->
        telemetryClient.trackEvent("sentence-term-updated-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryMap["reason"] = "Sentence term updated in NOMIS"
      telemetryClient.trackEvent("sentence-term-updated-ignored", telemetryMap, null)
    }
  }

  suspend fun deleteSentence(createdEvent: SentenceCreatedEvent) {
    val dpsSentenceId = createdEvent.additionalInformation.sentenceId
    val dpsCaseId = createdEvent.additionalInformation.courtCaseId
    val offenderNo = createdEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCaseId,
      "dpsSentenceId" to dpsSentenceId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(createdEvent.additionalInformation.source)) {
      runCatching {
        courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCaseId)?.also { mapping ->
          telemetryMap["nomisCourtCaseId"] = mapping.nomisCourtCaseId.toString()
          courtCaseMappingService.getMappingGivenSentenceIdOrNull(dpsSentenceId)?.also { sentenceMapping ->
            telemetryMap["nomisSentenceSeq"] = sentenceMapping.nomisSentenceSequence.toString()
            telemetryMap["nomisBookingId"] = sentenceMapping.nomisBookingId.toString()
            nomisApiService.deleteSentence(
              offenderNo = offenderNo,
              caseId = mapping.nomisCourtCaseId,
              sentenceSeq = sentenceMapping.nomisSentenceSequence,
            )
            tryToDeleteSentenceMapping(offenderNo, dpsSentenceId)
            telemetryClient.trackEvent("sentence-deleted-success", telemetryMap)
          } ?: also {
            telemetryMap["reason"] = "sentence mapping not found"
            telemetryClient.trackEvent("sentence-deleted-skipped", telemetryMap)
          }
        } ?: also {
          telemetryMap["reason"] = "no case mapping found for sentence - required for deletion"
          telemetryClient.trackEvent("sentence-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("sentence-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryMap["reason"] = "sentence deleted in nomis"
      telemetryClient.trackEvent("sentence-deleted-ignored", telemetryMap)
    }
  }

  suspend fun deleteSentenceTerm(createdEvent: PeriodLengthCreatedEvent) {
    val dpsSentenceId = createdEvent.additionalInformation.sentenceId
    val dpsTermId = createdEvent.additionalInformation.periodLengthId
    val dpsCaseId = createdEvent.additionalInformation.courtCaseId
    val offenderNo = createdEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCaseId,
      "dpsSentenceId" to dpsSentenceId,
      "dpsTermId" to dpsTermId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(createdEvent.additionalInformation.source)) {
      runCatching {
        courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCaseId)?.also { mapping ->
          telemetryMap["nomisCourtCaseId"] = mapping.nomisCourtCaseId.toString()
          courtCaseMappingService.getMappingGivenSentenceTermIdOrNull(dpsTermId)?.also { sentenceTermMapping ->
            telemetryMap["nomisSentenceSeq"] = sentenceTermMapping.nomisSentenceSequence.toString()
            telemetryMap["nomisTermSeq"] = sentenceTermMapping.nomisTermSequence.toString()
            telemetryMap["nomisBookingId"] = sentenceTermMapping.nomisBookingId.toString()
            nomisApiService.deleteSentenceTerm(
              offenderNo = offenderNo,
              caseId = mapping.nomisCourtCaseId,
              sentenceSeq = sentenceTermMapping.nomisSentenceSequence,
              termSeq = sentenceTermMapping.nomisTermSequence,
            )
            tryToDeleteSentenceTermMapping(offenderNo, dpsTermId)
            telemetryClient.trackEvent("sentence-term-deleted-success", telemetryMap)
          } ?: also {
            telemetryMap["reason"] = "sentence term mapping not found"
            telemetryClient.trackEvent("sentence-term-deleted-skipped", telemetryMap)
          }
        } ?: also {
          telemetryMap["reason"] = "Parent entity case not found - required for deletion"
          telemetryClient.trackEvent("sentence-term-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("sentence-term-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryMap["reason"] = "sentence term deleted in nomis"
      telemetryClient.trackEvent("sentence-term-deleted-ignored", telemetryMap)
    }
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)

  suspend fun refreshCaseReferences(createEvent: CaseReferencesUpdatedEvent) {
    val dpsCourtCaseId = createEvent.additionalInformation.courtCaseId
    val offenderNo = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCourtCaseId,
      "offenderNo" to offenderNo,
    )

    courtSentencingApiService.getCourtCaseOrNull(dpsCourtCaseId)?.let { dpsCourtCase ->
      courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = dpsCourtCaseId)
        ?.let { courtCaseMapping ->
          telemetryMap["nomisCourtCaseId"] = courtCaseMapping.nomisCourtCaseId.toString()
          val caseIdentifiers = dpsCourtCase.getNomisCaseIdentifiers()
          nomisApiService.refreshCaseReferences(
            offenderNo = offenderNo,
            nomisCourtCaseId = courtCaseMapping.nomisCourtCaseId,
            request = CaseIdentifierRequest(caseIdentifiers = caseIdentifiers),
          )
          telemetryMap["caseReferences"] = caseIdentifiers.toString()
          telemetryClient.trackEvent(
            "case-references-refreshed-success",
            telemetryMap,
            null,
          )
        } ?: let {
        telemetryMap["reason"] = "Parent entity not found"
        telemetryClient.trackEvent(
          "case-references-refreshed-failure",
          telemetryMap,
          null,
        )
        throw ParentEntityNotFoundRetry(
          "Attempt to refresh case references on a dps court case without a nomis mapping. Dps court case id: $dpsCourtCaseId not found",
        )
      }
    } ?: let {
      telemetryMap["reason"] = "DPS court case not found, possibly deleted"
      telemetryClient.trackEvent(
        "case-references-refreshed-ignored",
        telemetryMap,
        null,
      )
    }
  }

  fun LegacyCourtCase.getNomisCaseIdentifiers(): List<CaseIdentifier> {
    val caseReferences: List<CaseReferenceLegacyData> = this.caseReferences
    return caseReferences.map { caseReference ->
      CaseIdentifier(
        reference = caseReference.offenderCaseReference,
        createdDate = caseReference.updatedDate,
      )
    }
  }

  data class AdditionalInformation(
    val courtCaseId: String,
    val source: String,
  )

  data class CourtAppearanceAdditionalInformation(
    val courtAppearanceId: String,
    val source: String,
    val courtCaseId: String,
  )

  data class CourtChargeAdditionalInformation(
    val courtChargeId: String,
    val courtAppearanceId: String?,
    val source: String,
    val courtCaseId: String,
  )

  data class SentenceAdditionalInformation(
    val sentenceId: String,
    val courtAppearanceId: String,
    val source: String,
    val courtCaseId: String,
  )

  data class PeriodLengthAdditionalInformation(
    val sentenceId: String,
    val periodLengthId: String,
    val courtAppearanceId: String,
    val source: String,
    val courtCaseId: String,
  )

  data class CaseReferencesAdditionalInformation(
    val courtCaseId: String,
    val source: String,
  )

  data class RecallAdditionalInformation(
    val recallId: String,
    val previousRecallId: String?,
    val sentenceIds: List<String>,
    val source: String,
  )

  data class UpdateRecallAdditionalInformation(
    val recallId: String,
    val previousRecallId: String?,
    val sentenceIds: List<String>,
    val previousSentenceIds: List<String> = emptyList(),
    val source: String,
  )

  data class CourtCaseCreatedEvent(
    val additionalInformation: AdditionalInformation,
    val personReference: PersonReferenceList,
  )

  data class CourtAppearanceCreatedEvent(
    val additionalInformation: CourtAppearanceAdditionalInformation,
    val personReference: PersonReferenceList,
  )

  data class CourtChargeCreatedEvent(
    val additionalInformation: CourtChargeAdditionalInformation,
    val personReference: PersonReferenceList,
  )

  data class CaseReferencesUpdatedEvent(
    val additionalInformation: CaseReferencesAdditionalInformation,
    val personReference: PersonReferenceList,
  )

  data class SentenceCreatedEvent(
    val additionalInformation: SentenceAdditionalInformation,
    val personReference: PersonReferenceList,
  )

  data class PeriodLengthCreatedEvent(
    val additionalInformation: PeriodLengthAdditionalInformation,
    val personReference: PersonReferenceList,
  )

  data class RecallEvent(
    val additionalInformation: RecallAdditionalInformation,
    val personReference: PersonReferenceList,
  )
  data class UpdateRecallEvent(
    val additionalInformation: UpdateRecallAdditionalInformation,
    val personReference: PersonReferenceList,
  )
}

fun LegacyCourtCase.toNomisCourtCase(): CreateCourtCaseRequest = CreateCourtCaseRequest(
  // shared dto - will always be present,
  startDate = this.startDate!!,
  // shared dto - will always be present,
  courtId = this.courtId!!,
  caseReference = this.caseReference,
  // new LEG_CASE_TYP on NOMIS - "Not Entered"
  legalCaseType = "NE",
  status = if (this.active) {
    "A"
  } else {
    "I"
  },
)

fun LegacyCourtAppearance.toNomisCourtAppearance(
  courtEventCharges: List<Long>,
): CourtAppearanceRequest = CourtAppearanceRequest(
  eventDateTime = LocalDateTime.of(
    this.appearanceDate,
    LocalTime.parse(this.appearanceTime),
  ),
  courtEventType = if (this.appearanceTypeUuid.toString() == DPS_VIDEO_LINK) "VL" else "CRT",
  courtId = this.courtCode,
  outcomeReasonCode = this.nomisOutcomeCode,
  nextEventDateTime = this.nextCourtAppearance?.let { next ->
    next.appearanceTime?.let {
      LocalDateTime.of(
        next.appearanceDate,
        LocalTime.parse(it),
      )
    } ?: LocalDateTime.of(
      next.appearanceDate,
      LocalTime.MIDNIGHT,
    )
  },
  courtEventCharges = courtEventCharges,
  nextCourtId = this.nextCourtAppearance?.courtId,
)

fun LegacyCharge.toNomisCourtCharge(): OffenderChargeRequest = OffenderChargeRequest(
  offenceCode = this.offenceCode,
  offenceDate = this.offenceStartDate,
  offenceEndDate = this.offenceEndDate,
  resultCode1 = this.nomisOutcomeCode,
)

const val SENTENCE_LEVEL_IND = "IND"

fun LegacySentence.toNomisSentence(
  nomisChargeId: Long,
  nomisConsecutiveToSentenceSeq: Long?,
  nomisEventId: Long,
): CreateSentenceRequest = CreateSentenceRequest(
  offenderChargeIds = listOf(nomisChargeId),
  startDate = sentenceStartDate,
  // no end date provided,
  status = if (this.active) {
    "A"
  } else {
    "I"
  },
  sentenceCategory = this.sentenceCategory,
  sentenceCalcType = this.sentenceCalcType,
  sentenceLevel = SENTENCE_LEVEL_IND,
  fine = this.fineAmount,
  consecutiveToSentenceSeq = nomisConsecutiveToSentenceSeq,
  eventId = nomisEventId,
)

fun LegacyPeriodLength.toNomisSentenceTerm(): SentenceTermRequest = SentenceTermRequest(
  years = this.periodYears,
  months = this.periodMonths,
  days = this.periodDays,
  weeks = this.periodWeeks,
  sentenceTermType = this.sentenceTermCode,
  lifeSentenceFlag = this.isLifeSentence ?: false,
)

private fun CourtChargeBatchUpdateMappingDto.hasAnyMappingsToUpdate(): Boolean = this.courtChargesToCreate.isNotEmpty() || this.courtChargesToDelete.isNotEmpty()

data class SentenceIdAndAdjustmentIds(
  val sentenceId: SentenceId,
  val adjustmentIds: List<Long>,
)

data class SyncSentenceAdjustment(
  val offenderNo: String,
  val sentences: List<SentenceIdAndAdjustmentIds>,
)

data class OffenderSentenceResynchronisationEvent(
  val sentenceSeq: Int,
  val dpsSentenceUuid: String,
  val offenderNo: String,
  val bookingId: Long,
  val caseId: Long,
  val dpsAppearanceUuid: String,
  val dpsConsecutiveSentenceUuid: String?,
)

data class OffenderCaseBookingResynchronisationEvent(
  val offenderNo: String,
  val fromBookingId: Long,
  val toBookingId: Long,
  val caseIds: List<Long>,
  val casesMoved: List<CaseBookingChanged>,
)

data class CaseBookingChanged(
  val caseId: Long,
  val sentences: List<SentenceBookingChanged>,
)

data class SentenceBookingChanged(
  val sentenceSequence: Int,
)

data class OffenderCaseResynchronisationEvent(
  val dpsCaseUuid: String,
  val offenderNo: String,
  val caseId: Long,
)

data class CourtCaseBatchUpdateAndCreateMappingsWrapper(
  val mappings: CourtCaseBatchUpdateAndCreateMappingDto,
  val offenderNo: String,
  val clonedClonedCourtCaseDetails: ClonedCourtCaseDetails?,
)

data class RecallAppearanceAndCreateMappingsWrapper(
  val sentenceAdjustmentsActivated: List<uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceIdAndAdjustmentIds>,
  val mappings: CourtAppearanceRecallMappingsDto,
  val offenderNo: String,
  val clonedClonedCourtCaseDetails: ClonedCourtCaseDetails?,
)

data class ClonedCourtCaseDetails(
  val clonedCourtCaseIds: List<Long>,
  val fromBookingId: Long,
  val toBookingId: Long,
  val sentenceAdjustments: List<SentenceIdAndAdjustmentType>,
  val casesMoved: List<CaseBookingChanged>,
)

data class SentenceIdAndAdjustmentType(
  val sentenceId: SentenceId,
  val adjustmentIds: List<Long>,
)

fun BookingCourtCaseCloneResponse.toClonedCourtCaseDetails() = ClonedCourtCaseDetails(
  clonedCourtCaseIds = courtCases.map { it.sourceCourtCase.id },
  // there must always be at least once case cloned else we would never have a clone operation
  fromBookingId = courtCases.first().sourceCourtCase.bookingId,
  toBookingId = courtCases.first().courtCase.bookingId,
  sentenceAdjustments = sentenceAdjustments.map {
    SentenceIdAndAdjustmentType(
      sentenceId = it.sentenceId,
      adjustmentIds = it.adjustmentIds,
    )
  },
  casesMoved = courtCases.map {
    CaseBookingChanged(
      caseId = it.courtCase.id,
      sentences = it.courtCase.sentences.map { sentence ->
        SentenceBookingChanged(
          sentenceSequence = sentence.sentenceSeq.toInt(),
        )
      },
    )
  },
)

fun BookingCourtCaseCloneResponse.toCourtCaseBatchMappingDto(offenderNo: String): CourtCaseBatchUpdateAndCreateMappingsWrapper = CourtCaseBatchUpdateAndCreateMappingsWrapper(
  mappings =
  CourtCaseBatchUpdateAndCreateMappingDto(
    mappingsToCreate = CourtCaseBatchMappingDto(
      courtAppearances = emptyList(),
      courtCases = emptyList(),
      courtCharges = emptyList(),
      sentences = emptyList(),
      sentenceTerms = emptyList(),
      mappingType = CourtCaseBatchMappingDto.MappingType.DPS_CREATED,
    ),
    mappingsToUpdate = CourtCaseBatchUpdateMappingDto(
      courtCases = this.toCourtCases(),
      courtAppearances = this.toCourtAppearances(),
      courtCharges = this.toCourtCharges(),
      sentences = this.toSentences(),
      sentenceTerms = this.toSentenceTerms(),
    ),
  ),
  offenderNo = offenderNo,
  clonedClonedCourtCaseDetails = this.toClonedCourtCaseDetails(),
)

private fun BookingCourtCaseCloneResponse?.toCourtCases(): List<SimpleCourtSentencingIdPair> {
  val sourceCourtCases = this?.courtCases?.map { it.sourceCourtCase }
  val clonedCourtCases = this?.courtCases?.map { it.courtCase }

  return sourceCourtCases?.zip(clonedCourtCases!!)?.map { (source, cloned) ->
    SimpleCourtSentencingIdPair(
      fromNomisId = source.id,
      toNomisId = cloned.id,
    )
  } ?: emptyList()
}
private fun BookingCourtCaseCloneResponse?.toCourtCharges(): List<SimpleCourtSentencingIdPair> {
  val sourceCourtCharges = this?.courtCases?.flatMap { it.sourceCourtCase.offenderCharges }
  val clonedCourtCharges = this?.courtCases?.flatMap { it.courtCase.offenderCharges }

  return sourceCourtCharges?.zip(clonedCourtCharges!!)?.map { (source, cloned) ->
    SimpleCourtSentencingIdPair(
      fromNomisId = source.id,
      toNomisId = cloned.id,
    )
  } ?: emptyList()
}
private fun BookingCourtCaseCloneResponse?.toCourtAppearances(): List<SimpleCourtSentencingIdPair> {
  val sourceCourtAppearances = this?.courtCases?.flatMap { it.sourceCourtCase.courtEvents }
  val clonedCourtAppearances = this?.courtCases?.flatMap { it.courtCase.courtEvents }

  return sourceCourtAppearances?.zip(clonedCourtAppearances!!)?.map { (source, cloned) ->
    SimpleCourtSentencingIdPair(
      fromNomisId = source.id,
      toNomisId = cloned.id,
    )
  } ?: emptyList()
}

private fun BookingCourtCaseCloneResponse?.toSentences(): List<CourtSentenceIdPair> {
  val sourceSentences = this?.courtCases?.flatMap { it.sourceCourtCase.sentences }
  val clonedSentences = this?.courtCases?.flatMap { it.courtCase.sentences }

  return sourceSentences?.zip(clonedSentences!!)?.map { (source, cloned) ->
    CourtSentenceIdPair(
      fromNomisId = MappingSentenceId(
        nomisBookingId = source.bookingId,
        nomisSequence = source.sentenceSeq.toInt(),
      ),
      toNomisId = MappingSentenceId(
        nomisBookingId = cloned.bookingId,
        nomisSequence = cloned.sentenceSeq.toInt(),
      ),
    )
  } ?: emptyList()
}
private fun BookingCourtCaseCloneResponse?.toSentenceTerms(): List<CourtSentenceTermIdPair> {
  val sourceSentences = this?.courtCases?.flatMap { it.sourceCourtCase.sentences }
  val clonedSentences = this?.courtCases?.flatMap { it.courtCase.sentences }

  return sourceSentences?.zip(clonedSentences!!)?.flatMap { (source, cloned) ->
    source.sentenceTerms.zip(cloned.sentenceTerms).map { (sourceTerm, clonedTerm) ->
      CourtSentenceTermIdPair(
        fromNomisId = SentenceTermId(
          nomisSequence = sourceTerm.termSequence.toInt(),
          nomisSentenceId = MappingSentenceId(
            nomisBookingId = source.bookingId,
            nomisSequence = source.sentenceSeq.toInt(),
          ),
        ),
        toNomisId = SentenceTermId(
          nomisSequence = clonedTerm.termSequence.toInt(),
          nomisSentenceId = MappingSentenceId(
            nomisBookingId = cloned.bookingId,
            nomisSequence = cloned.sentenceSeq.toInt(),
          ),
        ),
      )
    }
  } ?: emptyList()
}

fun LocalDate?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE) ?: ""
private fun LegacyRecall.hasReturnToCustodyDate() = returnToCustodyDate != null || revocationDate != null
