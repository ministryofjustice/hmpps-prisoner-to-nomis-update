package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjustments

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDtoV2
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

      transform {
        val adjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
        val offenderNo = adjudication.reportedAdjudication.prisonerNumber
        telemetryMap["offenderNo"] = offenderNo
        nomisApiService.createAdjudication(offenderNo, adjudication.toNomisAdjudication())
      }
      saveMapping {
        // for now no mapping required since it is a shared business key
        // and all records will be migrated due to need of a merge of NOMIS and DPS data
      }
    }
  }

  override suspend fun retryCreateMapping(message: String) {}
}

internal fun ReportedAdjudicationResponseV2.toNomisAdjudication() = CreateAdjudicationRequest(
  adjudicationNumber = reportedAdjudication.adjudicationNumber,
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
    staffWitnessesUsernames = emptyList(), // Not stored in DPS so can not be synchronised
    staffVictimsUsernames = reportedAdjudication.offenceDetails.victimStaffUsername?.let { listOf(it) } ?: emptyList(),
    repairs = reportedAdjudication.damages.map { it.toNomisRepair() },
  ),
  charges = listOf(
    ChargeToCreate(
      offenceCode = reportedAdjudication.getOffenceCode(),
      offenceId = "${reportedAdjudication.adjudicationNumber}/1",
    ),
  ),
  evidence = reportedAdjudication.evidence.map { it.toNomisEvidence() },
)

private fun ReportedAdjudicationDtoV2.getOffenceCode() = if (this.didInciteOtherPrisoner()) {
  this.offenceDetails.offenceRule.withOthersNomisCode!!
} else {
  this.offenceDetails.offenceRule.nomisCode!!
}

private fun ReportedAdjudicationDtoV2.didInciteOtherPrisoner() = this.incidentRole.roleCode != null

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
