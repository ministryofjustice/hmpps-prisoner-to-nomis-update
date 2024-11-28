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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeNomisIdDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CaseIdentifier
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CaseIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourtAppearanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCourtCaseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ExistingOffenderChargeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.OffenderChargeRequest
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

  private fun isDpsCreated(source: String) =
    source != CreatingSystem.NOMIS.name

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
            ?: throw ParentEntityNotFoundRetry(
              "Attempt to create a court appearance on a dps court case without a nomis mapping. Dps court case id: $courtCaseId not found for DPS court appearance $courtAppearanceId",
            )

          val courtAppearance = courtSentencingApiService.getCourtAppearance(courtAppearanceId)

          val courtEventChargesToUpdate: MutableList<Pair<LegacyCharge, Long>> = mutableListOf()
          courtAppearance.charges.forEach { charge ->
            courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(charge.lifetimeUuid.toString())?.let { mapping ->
              courtEventChargesToUpdate.add(Pair(charge, mapping.nomisCourtChargeId))
            } ?: throw ParentEntityNotFoundRetry(
              "Attempt to associate a charge without a nomis charge mapping. Dps charge id: ${charge.lifetimeUuid} not found for DPS court appearance $courtAppearanceId",
            )
          }

          val nomisCourtAppearanceResponse =
            nomisApiService.createCourtAppearance(
              offenderNo,
              courtCaseMapping.nomisCourtCaseId,
              courtAppearance.toNomisCourtAppearance(
                courtEventChargesToUpdate = courtEventChargesToUpdate.map { it.first.toExistingNomisCourtCharge(it.second) },
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
          val courtCaseMapping = courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = courtCaseId)
            ?: throw ParentEntityNotFoundRetry(
              "Attempt to create a charge on a dps court case without a nomis Court Case mapping. Dps court case id: $courtCaseId not found for DPS charge $chargeId\"",
            )

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
    val offenderNo: String = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsChargeId" to chargeId,
      "dpsCourtCaseId" to courtCaseId,
      "offenderNo" to offenderNo,
    )
    if (isDpsCreated(source)) {
      runCatching {
        val courtCaseMapping =
          courtCaseMappingService.getMappingGivenCourtCaseId(courtCaseId).also {
            telemetryMap["nomisCourtCaseId"] = it.nomisCourtCaseId.toString()
          }

        val chargeMapping =
          courtCaseMappingService.getMappingGivenCourtChargeId(chargeId)
            .also {
              telemetryMap["nomisChargeId"] = it.nomisCourtChargeId.toString()
            }

        courtSentencingApiService.getCourtCharge(chargeId).let { dpsCharge ->

          nomisApiService.updateCourtCharge(
            offenderNo = offenderNo,
            nomisCourtCaseId = courtCaseMapping.nomisCourtCaseId,
            chargeId = chargeMapping.nomisCourtChargeId,
            request = dpsCharge.toNomisCourtCharge(),
          ).also {
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

        val courtEventChargesToUpdate: MutableList<Pair<LegacyCharge, Long>> = mutableListOf()

        dpsCourtAppearance.charges.forEach { charge ->
          courtCaseMappingService.getMappingGivenCourtChargeIdOrNull(charge.lifetimeUuid.toString())?.let { mapping ->
            courtEventChargesToUpdate.add(Pair(charge, mapping.nomisCourtChargeId))
          } ?: throw ParentEntityNotFoundRetry(
            "Attempt to associate a charge without a nomis charge mapping. Dps charge id: ${charge.lifetimeUuid} not found for DPS court appearance $courtAppearanceId",
          )
        }

        val nomisResponse = nomisApiService.updateCourtAppearance(
          offenderNo = offenderNo,
          nomisCourtCaseId = courtCaseMapping.nomisCourtCaseId,
          nomisCourtAppearanceId = courtAppearanceMapping.nomisCourtAppearanceId,
          request = dpsCourtAppearance.toNomisCourtAppearance(
            courtEventChargesToUpdate = courtEventChargesToUpdate.map { it.first.toExistingNomisCourtCharge(it.second) },
          ),
        )

        CourtChargeBatchUpdateMappingDto(
          courtChargesToCreate = emptyList(),
          // TODO confirm whether we need to delete charges here or whether we depend on the charge.deleted event
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

  suspend fun createRetry(message: CreateMappingRetryMessage<CourtCaseAllMappingDto>) =
    courtCaseMappingService.createMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "court-case-create-mapping-retry-success",
        message.telemetryAttributes,
        null,
      )
    }

  suspend fun createAppearanceRetry(message: CreateMappingRetryMessage<CourtAppearanceMappingDto>) =
    courtCaseMappingService.createAppearanceMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "court-appearance-create-mapping-retry-success",
        message.telemetryAttributes,
        null,
      )
    }

  suspend fun createChargeRetry(message: CreateMappingRetryMessage<CourtChargeMappingDto>) =
    courtCaseMappingService.createChargeMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "charge-create-mapping-retry-success",
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
      else -> throw IllegalArgumentException("Unknown entity type: ${baseMapping.entityName}")
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  suspend fun refreshCaseReferences(createEvent: CaseReferencesUpdatedEvent) {
    val dpsCourtCaseId = createEvent.additionalInformation.courtCaseId
    val offenderNo = createEvent.personReference.identifiers.first { it.type == "NOMS" }.value
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCourtCaseId,
      "offenderNo" to offenderNo,
    )

    courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId = dpsCourtCaseId)?.let { courtCaseMapping ->
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
        createdDate = caseReference.updatedDate.toString(),
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
}

// TODO wire up when new dto available
fun LegacyCourtCase.toNomisCourtCase(): CreateCourtCaseRequest {
  return CreateCourtCaseRequest(
    // latest appearance is always the only appearance during a Creat Case request
    // shared dto - will always be present,
    startDate = this.startDate!!,
    // shared dto - will always be present,
    courtId = this.courtId!!,
    caseReference = this.caseReference,
    // new LEG_CASE_TYP on NOMIS - "Not Entered"
    legalCaseType = "NE",
    // CASE_STS on NOMIS - no decision from DPS yet - defaulting to Active
    status = "A",
  )
}

fun LegacyCourtAppearance.toNomisCourtAppearance(
  courtEventChargesToUpdate: List<ExistingOffenderChargeRequest>,
): CourtAppearanceRequest {
  return CourtAppearanceRequest(
    // Just date without time recorded against the DPS appearance
    eventDateTime = LocalDateTime.of(
      this.appearanceDate,
      LocalTime.MIDNIGHT,
    ).toString(),
    courtEventType = "CRT",
    courtId = this.courtCode,
    outcomeReasonCode = this.nomisOutcomeCode,
    nextEventDateTime = this.nextCourtAppearance?.let { next ->
      next.appearanceTime?.let {
        LocalDateTime.of(
          next.appearanceDate,
          LocalTime.parse(it),
        ).toString()
      } ?: next.appearanceDate.toString()
    },
    courtEventChargesToUpdate = courtEventChargesToUpdate,
    courtEventChargesToCreate = emptyList(),
    nextCourtId = this.nextCourtAppearance?.courtId,
  )
}

fun LegacyCharge.toNomisCourtCharge(): OffenderChargeRequest = OffenderChargeRequest(
  offenceCode = this.offenceCode,
  offenceDate = this.offenceStartDate,
  offenceEndDate = this.offenceEndDate,
  resultCode1 = this.nomisOutcomeCode,
)

fun LegacyCharge.toExistingNomisCourtCharge(nomisId: Long): ExistingOffenderChargeRequest =
  ExistingOffenderChargeRequest(
    offenderChargeId = nomisId,
    offenceCode = this.offenceCode,
    offenceDate = this.offenceStartDate,
    offenceEndDate = this.offenceEndDate,
    resultCode1 = this.nomisOutcomeCode,
  )

private fun CourtChargeBatchUpdateMappingDto.hasAnyMappingsToUpdate(): Boolean =
  this.courtChargesToCreate.isNotEmpty() || this.courtChargesToDelete.isNotEmpty()
