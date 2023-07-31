package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjustments

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponseV2
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedDamageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedEvidenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ChargeToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.EvidenceToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.IncidentToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.RepairToCreate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime

@Service
class AdjudicationsService(
  private val telemetryClient: TelemetryClient,
  private val adjudicationRetryQueueService: AdjudicationsRetryQueueService,
  private val adjudicationsApiService: AdjudicationsApiService,
  private val nomisApiService: NomisApiService,
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
        null
      }
      transform {
        val adjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
        val offenderNo = adjudication.reportedAdjudication.prisonerNumber
        telemetryMap["offenderNo"] = offenderNo
        nomisApiService.createAdjudication(offenderNo, adjudication.toNomisAdjudication())

        "TODO return mapping dto"
      }
      saveMapping { }
    }
  }

  override suspend fun retryCreateMapping(message: String) {}
}

internal fun ReportedAdjudicationResponseV2.toNomisAdjudication() = CreateAdjudicationRequest(
  adjudicationNumber = reportedAdjudication.adjudicationNumber + 1_000_000, // So we can test basic skeleton in T3 a cheeky hack to ensure number is way out of range of the sequence
  incident = IncidentToCreate(
    reportingStaffUsername = reportedAdjudication.createdByUserId,
    incidentDate = reportedAdjudication.incidentDetails.dateTimeOfDiscovery.toLocalDate(),
    incidentTime = reportedAdjudication.incidentDetails.dateTimeOfDiscovery.toLocalTimeAtMinute().toString(),
    reportedDate = reportedAdjudication.createdDateTime.toLocalDate(),
    reportedTime = reportedAdjudication.createdDateTime.toLocalTimeAtMinute().toString(),
    internalLocationId = reportedAdjudication.incidentDetails.locationId,
    details = reportedAdjudication.incidentStatement.statement,
    prisonId = reportedAdjudication.originatingAgencyId,
    prisonerVictimsOffenderNumbers = reportedAdjudication.offenceDetails.victimPrisonersNumber?.let { listOf(it) } ?: emptyList(),
    staffWitnessesUsernames = emptyList(), // TODO since username not in API  possibly not stored
    staffVictimsUsernames = reportedAdjudication.offenceDetails.victimStaffUsername?.let { listOf(it) } ?: emptyList(),
    repairs = reportedAdjudication.damages.map { it.toNomisRepair() },
  ),
  charges = listOf(
    ChargeToCreate(offenceCode = reportedAdjudication.offenceDetails.offenceRule.nomisCode!!, offenceId = "${reportedAdjudication.adjudicationNumber + 1_000_000}/1"),
  ),
  evidence = reportedAdjudication.evidence.map { it.toNomisEvidence() },
)

private fun ReportedDamageDto.toNomisRepair() = RepairToCreate(
  typeCode = when (this.code) {
    ReportedDamageDto.Code.ELECTRICAL_REPAIR -> "ELEC"
    ReportedDamageDto.Code.PLUMBING_REPAIR -> "PLUM"
    ReportedDamageDto.Code.FURNITURE_OR_FABRIC_REPAIR -> "FABR"
    ReportedDamageDto.Code.LOCK_REPAIR -> "LOCK"
    ReportedDamageDto.Code.REDECORATION -> "DECO"
    ReportedDamageDto.Code.CLEANING -> "CLEA"
    ReportedDamageDto.Code.REPLACE_AN_ITEM -> "DECO" // best match possibly?
  },
  comment = this.details,
)

private fun ReportedEvidenceDto.toNomisEvidence() = EvidenceToCreate(
  typeCode = when (this.code) {
    ReportedEvidenceDto.Code.PHOTO -> "PHOTO"
    ReportedEvidenceDto.Code.BODY_WORN_CAMERA -> "OTHER" // maybe PHOTO ?
    ReportedEvidenceDto.Code.CCTV -> "OTHER" // maybe PHOTO ?
    ReportedEvidenceDto.Code.BAGGED_AND_TAGGED -> "EVI_BAG"
  },
  detail = this.details,
)

private fun String.toLocalDate() = LocalDateTime.parse(this).toLocalDate()
private fun String.toLocalTimeAtMinute() = LocalDateTime.parse(this).toLocalTime().withSecond(0).withNano(0)

data class AdjudicationAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
)

data class AdjudicationCreatedEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)
