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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacySentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeNomisIdDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifier
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtAppearanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtCaseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceTermRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class CourtSentencingService(
  private val courtSentencingApiService: CourtSentencingApiService,
  private val nomisApiService: NomisApiService,
  private val courtCaseMappingService: CourtCaseMappingService,
  private val courtSentencingRetryQueueService: CourtSentencingRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  enum class EntityType(val displayName: String) {
    COURT_CASE("court-case"),
    COURT_APPEARANCE("court-appearance"),
    COURT_CHARGE("charge"),
    SENTENCE("sentence"),
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
      synchronise {
        name = EntityType.COURT_APPEARANCE.displayName
        telemetryClient = this@CourtSentencingService.telemetryClient
        retryQueueService = courtSentencingRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          courtCaseMappingService.getMappingGivenCourtAppearanceIdOrNull(courtAppearanceId)
        }

        transform {
          val courtCaseMapping = courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = courtCaseId)
            ?: let {
              telemetryMap["reason"] = "Parent entity not found"
              throw ParentEntityNotFoundRetry(
                "Attempt to create a court appearance on a dps court case without a nomis mapping. Dps court case id: $courtCaseId not found for DPS court appearance $courtAppearanceId",
              )
            }

          val courtAppearance = courtSentencingApiService.getCourtAppearance(courtAppearanceId)

          val courtEventChargesToUpdate: MutableList<Long> = mutableListOf()
          courtAppearance.charges.forEach { charge ->
            courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(charge.lifetimeUuid.toString())?.let { mapping ->
              courtEventChargesToUpdate.add(mapping.nomisCourtChargeId)
            } ?: let {
              telemetryMap["reason"] = "Parent entity not found"
              throw ParentEntityNotFoundRetry(
                "Attempt to associate a charge without a nomis charge mapping. Dps charge id: ${charge.lifetimeUuid} not found for DPS court appearance $courtAppearanceId",
              )
            }
          }

          val nomisCourtAppearanceResponse =
            nomisApiService.createCourtAppearance(
              offenderNo,
              courtCaseMapping.nomisCourtCaseId,
              courtAppearance.toNomisCourtAppearance(
                courtEventCharges = courtEventChargesToUpdate.map { it }
                  .also { telemetryMap["courtEventCharges"] = it.toString() },
              ),
            )

          CourtAppearanceMappingDto(
            dpsCourtAppearanceId = courtAppearanceId,
            nomisCourtAppearanceId = nomisCourtAppearanceResponse.id,
          ).also {
            telemetryMap["nomisCourtCaseId"] = courtCaseMapping.nomisCourtCaseId.toString()
          }
        }
        saveMapping { courtCaseMappingService.createAppearanceMapping(it) }
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

        courtSentencingApiService.getCourtCharge(chargeId).let { dpsCharge ->

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

  suspend fun createAppearanceRetry(message: CreateMappingRetryMessage<CourtAppearanceMappingDto>) = courtCaseMappingService.createAppearanceMapping(message.mapping).also {
    telemetryClient.trackEvent(
      "court-appearance-create-mapping-retry-success",
      message.telemetryAttributes,
      null,
    )
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

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (baseMapping.entityName) {
      EntityType.COURT_CASE.displayName -> createRetry(message.fromJson())
      EntityType.COURT_APPEARANCE.displayName -> createAppearanceRetry(message.fromJson())
      EntityType.COURT_CHARGE.displayName -> createChargeRetry(message.fromJson())
      EntityType.SENTENCE.displayName -> createSentenceRetry(message.fromJson())
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
                      telemetryMap["nomisSentenceSeq"] = nomisSentenceResponse.sentenceSeq.toString()
                      telemetryMap["nomisbookingId"] = nomisSentenceResponse.bookingId.toString()
                      telemetryMap["nomisChargeId"] = chargeMapping.nomisCourtChargeId.toString()
                      telemetryMap["nomisConsecutiveSentenceSequence"] = consecutiveSentenceSeq.toString()

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
            telemetryClient.trackEvent("sentence-deleted-skipped", telemetryMap)
          }
        } ?: also {
          telemetryClient.trackEvent("sentence-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("sentence-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("sentence-deleted-ignored", telemetryMap)
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

    courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = dpsCourtCaseId)
      ?.let { courtCaseMapping ->
        telemetryMap["nomisCourtCaseId"] = courtCaseMapping.nomisCourtCaseId.toString()
        courtSentencingApiService.getCourtCase(dpsCourtCaseId).let { dpsCourtCase ->
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
        }
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

  data class CaseReferencesAdditionalInformation(
    val courtCaseId: String,
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
  courtEventType = "CRT",
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
): CreateSentenceRequest {
  val terms: List<SentenceTermRequest> = this.periodLengths.map { it.toNomisSentenceTerm() }
  return CreateSentenceRequest(
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
    sentenceTerms = terms,

    fine = this.fineAmount,
    consecutiveToSentenceSeq = nomisConsecutiveToSentenceSeq,
    eventId = nomisEventId,
  )
}

fun LegacyPeriodLength.toNomisSentenceTerm(): SentenceTermRequest = SentenceTermRequest(
  years = this.periodYears,
  months = this.periodMonths,
  days = this.periodDays,
  weeks = this.periodWeeks,
  sentenceTermType = this.sentenceTermCode,
  lifeSentenceFlag = this.isLifeSentence ?: false,
)

private fun CourtChargeBatchUpdateMappingDto.hasAnyMappingsToUpdate(): Boolean = this.courtChargesToCreate.isNotEmpty() || this.courtChargesToDelete.isNotEmpty()
