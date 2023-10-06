package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingOutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeHistoryDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedDamageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedEvidenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ChargeToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingResultRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.EvidenceToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.EvidenceToUpdateOrAdd
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.IncidentToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.RepairToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.RepairToUpdateOrAdd
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateEvidenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateRepairsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime

@Service
class AdjudicationsService(
  private val telemetryClient: TelemetryClient,
  private val adjudicationRetryQueueService: AdjudicationsRetryQueueService,
  private val adjudicationMappingService: AdjudicationsMappingService,
  private val hearingMappingService: HearingsMappingService,
  private val adjudicationsApiService: AdjudicationsApiService,
  private val nomisApiService: NomisApiService,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun createAdjudication(createEvent: AdjudicationCreatedEvent) {
    val chargeNumber = createEvent.additionalInformation.chargeNumber
    val prisonId: String = createEvent.additionalInformation.prisonId
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
    )
    synchronise {
      name = "adjudication"
      telemetryClient = this@AdjudicationsService.telemetryClient
      retryQueueService = adjudicationRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        adjudicationMappingService.getMappingGivenChargeNumberOrNull(createEvent.additionalInformation.chargeNumber)
      }

      transform {
        val adjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
        val offenderNo = adjudication.reportedAdjudication.prisonerNumber
        telemetryMap["offenderNo"] = offenderNo
        val nomisAdjudicationResponse =
          nomisApiService.createAdjudication(offenderNo, adjudication.toNomisAdjudication())

        AdjudicationMappingDto(
          adjudicationNumber = nomisAdjudicationResponse.adjudicationNumber,
          chargeSequence = nomisAdjudicationResponse.adjudicationSequence,
          chargeNumber = createEvent.additionalInformation.chargeNumber,
        )
      }
      saveMapping { adjudicationMappingService.createMapping(it) }
    }
  }

  suspend fun createRetry(message: CreateMappingRetryMessage<AdjudicationMappingDto>) {
    adjudicationMappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "adjudication-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  suspend fun createHearingRetry(message: CreateMappingRetryMessage<AdjudicationHearingMappingDto>) {
    hearingMappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "hearing-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<BaseAdjudicationMappingDto> = message.fromJson()
    when (baseMapping.mapping.mappingEntity) {
      AdjudicationMappingEntity.ADJUDICATION -> createRetry(message.fromJson())
      AdjudicationMappingEntity.HEARING -> createHearingRetry(message.fromJson())
    }
  }

  suspend fun updateAdjudicationDamages(damagesUpdateEvent: AdjudicationDamagesUpdateEvent) {
    val chargeNumber = damagesUpdateEvent.additionalInformation.chargeNumber
    val prisonId: String = damagesUpdateEvent.additionalInformation.prisonId
    val offenderNo: String = damagesUpdateEvent.additionalInformation.prisonerNumber
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
      "offenderNo" to offenderNo,
    )

    runCatching {
      val mapping =
        adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val dpsAdjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
      nomisApiService.updateAdjudicationRepairs(
        mapping.adjudicationNumber,
        UpdateRepairsRequest(repairs = dpsAdjudication.reportedAdjudication.damages.map { it.toNomisRepairForUpdate() }),
      ).also {
        telemetryMap["repairCount"] = it.repairs.size.toString()
      }

      telemetryClient.trackEvent(
        "adjudication-damages-updated-success",
        telemetryMap,
      )
    }.onFailure { e ->
      telemetryClient.trackEvent("adjudication-damages-updated-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun updateAdjudicationEvidence(evidenceUpdateEvent: AdjudicationEvidenceUpdateEvent) {
    val chargeNumber = evidenceUpdateEvent.additionalInformation.chargeNumber
    val prisonId: String = evidenceUpdateEvent.additionalInformation.prisonId
    val offenderNo: String = evidenceUpdateEvent.additionalInformation.prisonerNumber
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
      "offenderNo" to offenderNo,
    )

    runCatching {
      val mapping =
        adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val dpsAdjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
      nomisApiService.updateAdjudicationEvidence(
        mapping.adjudicationNumber,
        UpdateEvidenceRequest(evidence = dpsAdjudication.reportedAdjudication.evidence.map { it.toNomisUpdateEvidence() }),
      ).also {
        telemetryMap["evidenceCount"] = it.evidence.size.toString()
      }

      telemetryClient.trackEvent(
        "adjudication-evidence-updated-success",
        telemetryMap,
      )
    }.onFailure { e ->
      telemetryClient.trackEvent("adjudication-evidence-updated-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun createHearing(createEvent: HearingEvent) {
    val chargeNumber = createEvent.additionalInformation.chargeNumber
    val prisonId: String = createEvent.additionalInformation.prisonId
    val prisonerNumber: String = createEvent.additionalInformation.prisonerNumber
    val dpsHearingId: String = createEvent.additionalInformation.hearingId
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
      "prisonerNumber" to prisonerNumber,
      "dpsHearingId" to dpsHearingId,
    )
    synchronise {
      name = "hearing"
      telemetryClient = this@AdjudicationsService.telemetryClient
      retryQueueService = adjudicationRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        hearingMappingService.getMappingGivenDpsHearingIdOrNull(createEvent.additionalInformation.hearingId)
      }

      transform {
        val adjudicationNumber =
          adjudicationMappingService.getMappingGivenChargeNumber(chargeNumber).adjudicationNumber
            .also {
              telemetryMap["adjudicationNumber"] = it.toString()
            }
        log.info("Hearing: $dpsHearingId with charge number: $chargeNumber has adjudication number: $adjudicationNumber")
        val hearing = adjudicationsApiService.getCharge(
          chargeNumber,
          prisonId,
        ).reportedAdjudication.hearings.firstOrNull { it.id.toString() == dpsHearingId } ?: throw IllegalStateException(
          "Hearing $dpsHearingId not found for DPS adjudication with charge no $chargeNumber",
        )
        val nomisAdjudicationResponse =
          nomisApiService.createHearing(adjudicationNumber, hearing.toNomisCreateHearing())

        telemetryMap["nomisHearingId"] = nomisAdjudicationResponse.hearingId.toString()

        AdjudicationHearingMappingDto(
          dpsHearingId = createEvent.additionalInformation.hearingId,
          nomisHearingId = nomisAdjudicationResponse.hearingId,
        )
      }
      saveMapping { hearingMappingService.createMapping(it) }
    }
  }

  suspend fun updateHearing(updateEvent: HearingEvent) {
    val eventData: HearingAdditionalInformation = updateEvent.additionalInformation
    val telemetryMap = updateEvent.additionalInformation.toTelemetryMap()

    runCatching {
      val adjudicationNumber =
        adjudicationMappingService.getMappingGivenChargeNumber(eventData.chargeNumber).adjudicationNumber
          .also {
            telemetryMap["adjudicationNumber"] = it.toString()
          }
      val nomisHearingId =
        hearingMappingService.getMappingGivenDpsHearingId(eventData.hearingId).nomisHearingId
          .also { telemetryMap["nomisHearingId"] = it.toString() }

      adjudicationsApiService.getCharge(
        eventData.chargeNumber,
        eventData.prisonId,
      ).reportedAdjudication.hearings.firstOrNull { it.id.toString() == eventData.hearingId }?.let {
        nomisApiService.updateHearing(adjudicationNumber, nomisHearingId, it.toNomisUpdateHearing())
        telemetryClient.trackEvent("hearing-updated-success", telemetryMap, null)
      } ?: throw IllegalStateException(
        "Hearing ${eventData.hearingId} not found for DPS adjudication with charge no ${eventData.chargeNumber}",
      )
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-updated-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun deleteHearing(deleteEvent: HearingEvent) {
    val eventData: HearingAdditionalInformation = deleteEvent.additionalInformation
    val telemetryMap = deleteEvent.additionalInformation.toTelemetryMap()

    runCatching {
      val adjudicationNumber =
        adjudicationMappingService.getMappingGivenChargeNumber(eventData.chargeNumber).adjudicationNumber
          .also {
            telemetryMap["adjudicationNumber"] = it.toString()
          }
      val nomisHearingId =
        hearingMappingService.getMappingGivenDpsHearingId(eventData.hearingId).nomisHearingId
          .also { telemetryMap["nomisHearingId"] = it.toString() }

      nomisApiService.deleteHearing(adjudicationNumber, nomisHearingId).also {
        hearingMappingService.deleteMappingGivenDpsHearingId(eventData.hearingId)
      }
    }.onSuccess {
      telemetryClient.trackEvent("hearing-deleted-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-deleted-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun createHearingCompleted(createEvent: HearingEvent) {
    val event = createEvent.additionalInformation
    val telemetryMap = event.toTelemetryMap()

    val charge = adjudicationsApiService.getCharge(
      event.chargeNumber,
      event.prisonId,
    )

    runCatching {
      val outcome = charge.reportedAdjudication.outcomes.firstOrNull { it.hearing?.id.toString() == event.hearingId }
        ?: throw IllegalStateException(
          "Outcome not found for Hearing ${event.hearingId} in DPS adjudication with charge no ${event.chargeNumber}",
        )

      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(event.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
            telemetryMap["chargeSequence"] = it.chargeSequence.toString()
          }

      val hearingMapping =
        hearingMappingService.getMappingGivenDpsHearingIdOrNull(dpsHearingId = event.hearingId.toString())
          ?.also { telemetryMap["nomisHearingId"] = it.nomisHearingId.toString() }
          ?: throw IllegalStateException(
            "Hearing mapping for dps hearing id: ${event.hearingId} not found for DPS adjudication with charge no ${event.chargeNumber}",
          )

      nomisApiService.createHearingResult(
        adjudicationNumber = adjudicationMapping.adjudicationNumber,
        hearingId = hearingMapping.nomisHearingId,
        chargeSequence = adjudicationMapping.chargeSequence,
        request = outcome.toNomisCreateHearingResult(),
      )
    }.onSuccess {
      telemetryClient.trackEvent("hearing-result-created-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-result-created-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun deleteHearingCompleted(deleteEvent: HearingEvent) {
    val eventData: HearingAdditionalInformation = deleteEvent.additionalInformation
    val telemetryMap = deleteEvent.additionalInformation.toTelemetryMap()

    runCatching {
      val adjudicationMapping =
        adjudicationMappingService.getMappingGivenChargeNumber(eventData.chargeNumber)
          .also {
            telemetryMap["adjudicationNumber"] = it.adjudicationNumber.toString()
          }
      val hearingMapping =
        hearingMappingService.getMappingGivenDpsHearingId(eventData.hearingId)
          .also { telemetryMap["nomisHearingId"] = it.nomisHearingId.toString() }

      nomisApiService.deleteHearingResult(
        adjudicationMapping.adjudicationNumber,
        hearingMapping.nomisHearingId,
        adjudicationMapping.chargeSequence,
      )
    }.onSuccess {
      telemetryClient.trackEvent("hearing-result-deleted-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("hearing-result-deleted-failed", telemetryMap, null)
      throw e
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

private fun HearingDto.toNomisUpdateHearing(): UpdateHearingRequest = UpdateHearingRequest(
  hearingType = this.oicHearingType.name,
  hearingDate = this.dateTimeOfHearing.toLocalDate(),
  hearingTime = this.dateTimeOfHearing.toLocalTimeAtMinute().toString(),
  internalLocationId = this.locationId,
)

// logic for determining the NOMIS findingCode:
// If separate outcome block exists use code, else use code from hearing.outcome
private fun OutcomeHistoryDto.toNomisCreateHearingResult(): CreateHearingResultRequest {
  val separateOutcome: OutcomeDto? = this.outcome?.outcome
  val hearing = this.hearing!!
  val findingCode = separateOutcome?.code?.name ?: hearing.outcome!!.code.name

  return CreateHearingResultRequest(
    pleaFindingCode = hearing.outcome!!.plea?.name ?: HearingOutcomeDto.Plea.NOT_ASKED.name,
    findingCode = toNomisFindingCode(findingCode),
    adjudicatorUsername = getAdjudicatorUsernameForInternalHearingOnly(
      hearing.oicHearingType.name,
      hearing.outcome.adjudicator,
    ),
  )
}

private fun toNomisFindingCode(outcome: String) = when (outcome) {
  "CHARGE_PROVED" -> "PROVED"
  "ADJOURN" -> "S"
  "DISMISSED" -> "D" // TODO confirm they still want to map to D
  "REFER_POLICE" -> "REF_POLICE"
  "REFER_INAD" -> "REF_INAD" // TODO confirm behaviour with john
  else -> outcome
}

private fun getAdjudicatorUsernameForInternalHearingOnly(hearingType: String, adjudicator: String) =
  when (hearingType) {
    HearingDto.OicHearingType.INAD_ADULT.name, HearingDto.OicHearingType.INAD_YOI.name -> null
    else -> adjudicator
  }

private fun HearingAdditionalInformation.toTelemetryMap(): MutableMap<String, String> = mutableMapOf(
  "chargeNumber" to this.chargeNumber,
  "prisonId" to this.prisonId,
  "prisonerNumber" to this.prisonerNumber,
  "dpsHearingId" to this.hearingId,
)

internal fun ReportedAdjudicationResponse.toNomisAdjudication() = CreateAdjudicationRequest(
  adjudicationNumber = reportedAdjudication.chargeNumber.toNomisAdjudicationNumber(),
  incident = IncidentToCreate(
    reportingStaffUsername = reportedAdjudication.createdByUserId,
    incidentDate = reportedAdjudication.incidentDetails.dateTimeOfDiscovery.toLocalDate(),
    incidentTime = reportedAdjudication.incidentDetails.dateTimeOfDiscovery.toLocalTimeAtMinute().toString(),
    reportedDate = reportedAdjudication.createdDateTime.toLocalDate(),
    reportedTime = reportedAdjudication.createdDateTime.toLocalTimeAtMinute().toString(),
    internalLocationId = reportedAdjudication.incidentDetails.locationId,
    details = reportedAdjudication.incidentStatement.statement,
    prisonId = reportedAdjudication.originatingAgencyId,
    prisonerVictimsOffenderNumbers = reportedAdjudication.offenceDetails.victimPrisonersNumber?.let { listOf(it) }
      ?: emptyList(),
    staffWitnessesUsernames = emptyList(), // Not stored in DPS so can not be synchronised
    staffVictimsUsernames = reportedAdjudication.offenceDetails.victimStaffUsername?.let { listOf(it) } ?: emptyList(),
    repairs = reportedAdjudication.damages.map { it.toNomisRepairForCreate() },
  ),
  charges = listOf(
    ChargeToCreate(
      offenceCode = reportedAdjudication.getOffenceCode(),
      offenceId = "${reportedAdjudication.chargeNumber.toNomisAdjudicationNumber()}/1",
    ),
  ),
  evidence = reportedAdjudication.evidence.map { it.toNomisCreateEvidence() },
)

// DPS charge number are either "12345" or "12345-1" - but for new ones it will always
// be just "12345" so we can just convert to long for now without parsing
// Once we tackle updates on migrated records this code will need changing to parse the number
private fun String.toNomisAdjudicationNumber(): Long = this.toLong()

private fun ReportedAdjudicationDto.getOffenceCode() = if (this.didInciteOtherPrisoner()) {
  this.offenceDetails.offenceRule.withOthersNomisCode!!
} else {
  this.offenceDetails.offenceRule.nomisCode!!
}

private fun ReportedAdjudicationDto.didInciteOtherPrisoner() = this.incidentRole.roleCode != null

private fun ReportedDamageDto.toNomisRepairForCreate() = RepairToCreate(
  typeCode = this.code.toNomisCreateEnum(),
  comment = this.details,
)

private fun ReportedDamageDto.toNomisRepairForUpdate() = RepairToUpdateOrAdd(
  typeCode = this.code.toNomisUpdateEnum(),
  comment = this.details,
)

private fun ReportedDamageDto.Code.toNomisUpdateEnum(): RepairToUpdateOrAdd.TypeCode = when (this) {
  ReportedDamageDto.Code.ELECTRICAL_REPAIR -> RepairToUpdateOrAdd.TypeCode.ELEC
  ReportedDamageDto.Code.PLUMBING_REPAIR -> RepairToUpdateOrAdd.TypeCode.PLUM
  ReportedDamageDto.Code.FURNITURE_OR_FABRIC_REPAIR -> RepairToUpdateOrAdd.TypeCode.FABR
  ReportedDamageDto.Code.LOCK_REPAIR -> RepairToUpdateOrAdd.TypeCode.LOCK
  ReportedDamageDto.Code.REDECORATION -> RepairToUpdateOrAdd.TypeCode.DECO
  ReportedDamageDto.Code.CLEANING -> RepairToUpdateOrAdd.TypeCode.CLEA
  ReportedDamageDto.Code.REPLACE_AN_ITEM -> RepairToUpdateOrAdd.TypeCode.DECO // best match possibly?
}

private fun ReportedDamageDto.Code.toNomisCreateEnum(): RepairToCreate.TypeCode = when (this) {
  ReportedDamageDto.Code.ELECTRICAL_REPAIR -> RepairToCreate.TypeCode.ELEC
  ReportedDamageDto.Code.PLUMBING_REPAIR -> RepairToCreate.TypeCode.PLUM
  ReportedDamageDto.Code.FURNITURE_OR_FABRIC_REPAIR -> RepairToCreate.TypeCode.FABR
  ReportedDamageDto.Code.LOCK_REPAIR -> RepairToCreate.TypeCode.LOCK
  ReportedDamageDto.Code.REDECORATION -> RepairToCreate.TypeCode.DECO
  ReportedDamageDto.Code.CLEANING -> RepairToCreate.TypeCode.CLEA
  ReportedDamageDto.Code.REPLACE_AN_ITEM -> RepairToCreate.TypeCode.DECO
}

private fun ReportedEvidenceDto.toNomisCreateEvidence() = EvidenceToCreate(
  typeCode = when (this.code) {
    ReportedEvidenceDto.Code.PHOTO -> EvidenceToCreate.TypeCode.PHOTO
    ReportedEvidenceDto.Code.BODY_WORN_CAMERA -> EvidenceToCreate.TypeCode.OTHER
    ReportedEvidenceDto.Code.CCTV -> EvidenceToCreate.TypeCode.OTHER
    ReportedEvidenceDto.Code.BAGGED_AND_TAGGED -> EvidenceToCreate.TypeCode.EVI_BAG
    ReportedEvidenceDto.Code.OTHER -> EvidenceToCreate.TypeCode.OTHER
  },
  detail = this.details,
)

private fun ReportedEvidenceDto.toNomisUpdateEvidence() = EvidenceToUpdateOrAdd(
  typeCode = when (this.code) {
    ReportedEvidenceDto.Code.PHOTO -> EvidenceToUpdateOrAdd.TypeCode.PHOTO
    ReportedEvidenceDto.Code.BODY_WORN_CAMERA -> EvidenceToUpdateOrAdd.TypeCode.OTHER
    ReportedEvidenceDto.Code.CCTV -> EvidenceToUpdateOrAdd.TypeCode.OTHER
    ReportedEvidenceDto.Code.BAGGED_AND_TAGGED -> EvidenceToUpdateOrAdd.TypeCode.EVI_BAG
    ReportedEvidenceDto.Code.OTHER -> EvidenceToUpdateOrAdd.TypeCode.OTHER
  },
  detail = this.details,
)

private fun String.toLocalDate() = LocalDateTime.parse(this).toLocalDate()
private fun String.toLocalTimeAtMinute() = LocalDateTime.parse(this).toLocalTime().withSecond(0).withNano(0)

data class AdjudicationAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
)

data class AdjudicationCreatedEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)

data class AdjudicationDamagesUpdateEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)

data class AdjudicationEvidenceUpdateEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)

private fun HearingDto.toNomisCreateHearing() = CreateHearingRequest(
  hearingType = this.oicHearingType.name,
  hearingDate = this.dateTimeOfHearing.toLocalDate(),
  hearingTime = this.dateTimeOfHearing.toLocalTimeAtMinute().toString(),
  agencyId = this.agencyId,
  internalLocationId = this.locationId,
)

data class HearingAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
  val hearingId: String,
)

data class HearingEvent(
  val additionalInformation: HearingAdditionalInformation,
)
