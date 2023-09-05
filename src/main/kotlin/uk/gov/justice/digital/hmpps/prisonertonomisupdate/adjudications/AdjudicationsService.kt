package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedDamageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedEvidenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ChargeToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.EvidenceToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.IncidentToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.RepairToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.RepairToUpdateOrAdd
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
    when (baseMapping.mapping.type) { // type enum
      "ADJUDICATION" -> createRetry(message.fromJson())
      "HEARING" -> createHearingRetry(message.fromJson())
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

  suspend fun createHearing(createEvent: HearingCreatedEvent) {
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
        val hearing = adjudicationsApiService.getCharge(
          chargeNumber,
          prisonId,
        ).reportedAdjudication.hearings.firstOrNull { it.id.toString() == dpsHearingId } ?: throw IllegalStateException(
          "Hearing $dpsHearingId not found for DPS adjudication with charge no $chargeNumber",
        )
        val nomisAdjudicationResponse =
          nomisApiService.createHearing(chargeNumber.toNomisAdjudicationNumber(), hearing.toNomisHearing())

        telemetryMap["nomisHearingId"] = nomisAdjudicationResponse.hearingId.toString()

        AdjudicationHearingMappingDto(
          dpsHearingId = createEvent.additionalInformation.hearingId,
          nomisHearingId = nomisAdjudicationResponse.hearingId,
        )
      }
      saveMapping { hearingMappingService.createMapping(it) }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

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
  evidence = reportedAdjudication.evidence.map { it.toNomisEvidence() },
)

// DPS charge number are either "12345" or "12345-1" - but for new ones it will always
// be just "12345" so we can just convert to long for now without parsing
// Once we tackle updates on migrated records this code will need changing to parse the number
internal fun String.toNomisAdjudicationNumber(): Long = this.toLong()

private fun ReportedAdjudicationDto.getOffenceCode() = if (this.didInciteOtherPrisoner()) {
  this.offenceDetails.offenceRule.withOthersNomisCode!!
} else {
  this.offenceDetails.offenceRule.nomisCode!!
}

private fun ReportedAdjudicationDto.didInciteOtherPrisoner() = this.incidentRole.roleCode != null

private fun ReportedDamageDto.toNomisRepairForCreate() = RepairToCreate(
  typeCode = this.code.toNomisEnum().name, // TODO - this API provides no enum, but should do
  comment = this.details,
)

private fun ReportedDamageDto.toNomisRepairForUpdate() = RepairToUpdateOrAdd(
  typeCode = this.code.toNomisEnum(),
  comment = this.details,
)

private fun ReportedDamageDto.Code.toNomisEnum(): RepairToUpdateOrAdd.TypeCode = when (this) {
  ReportedDamageDto.Code.ELECTRICAL_REPAIR -> RepairToUpdateOrAdd.TypeCode.ELEC
  ReportedDamageDto.Code.PLUMBING_REPAIR -> RepairToUpdateOrAdd.TypeCode.PLUM
  ReportedDamageDto.Code.FURNITURE_OR_FABRIC_REPAIR -> RepairToUpdateOrAdd.TypeCode.FABR
  ReportedDamageDto.Code.LOCK_REPAIR -> RepairToUpdateOrAdd.TypeCode.LOCK
  ReportedDamageDto.Code.REDECORATION -> RepairToUpdateOrAdd.TypeCode.DECO
  ReportedDamageDto.Code.CLEANING -> RepairToUpdateOrAdd.TypeCode.CLEA
  ReportedDamageDto.Code.REPLACE_AN_ITEM -> RepairToUpdateOrAdd.TypeCode.DECO // best match possibly?
}

private fun ReportedEvidenceDto.toNomisEvidence() = EvidenceToCreate(
  typeCode = when (this.code) {
    ReportedEvidenceDto.Code.PHOTO -> "PHOTO"
    ReportedEvidenceDto.Code.BODY_WORN_CAMERA -> "OTHER" // maybe PHOTO ?
    ReportedEvidenceDto.Code.CCTV -> "OTHER" // maybe PHOTO ?
    ReportedEvidenceDto.Code.BAGGED_AND_TAGGED -> "EVI_BAG"
    ReportedEvidenceDto.Code.OTHER -> "OTHER"
  },
  detail = this.details,
)

internal fun String.toLocalDate() = LocalDateTime.parse(this).toLocalDate()
internal fun String.toLocalTimeAtMinute() = LocalDateTime.parse(this).toLocalTime().withSecond(0).withNano(0)

data class AdjudicationAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
)

data class AdjudicationCreatedEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)

data class AdjudicationDamagesAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
  val prisonerNumber: String,
)

data class AdjudicationDamagesUpdateEvent(
  val additionalInformation: AdjudicationDamagesAdditionalInformation,
)

private fun HearingDto.toNomisHearing() = CreateHearingRequest(
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

data class HearingCreatedEvent(
  val additionalInformation: HearingAdditionalInformation,
)
