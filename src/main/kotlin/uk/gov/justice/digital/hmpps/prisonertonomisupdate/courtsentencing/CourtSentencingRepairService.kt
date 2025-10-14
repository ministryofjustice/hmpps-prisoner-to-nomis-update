package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.ConflictException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList

@Service
class CourtSentencingRepairService(
  private val telemetryClient: TelemetryClient,
  private val courtSentencingService: CourtSentencingService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val dpsApiService: CourtSentencingApiService,
  private val courtCaseMappingService: CourtSentencingMappingService,
) {
  suspend fun chargeInsertedRepair(request: CourtChargeRequest) {
    val event: CourtSentencingService.CourtChargeCreatedEvent = CourtSentencingService.CourtChargeCreatedEvent(
      additionalInformation = CourtSentencingService.CourtChargeAdditionalInformation(
        courtChargeId = request.dpsChargeId,
        courtCaseId = request.dpsCaseId,
        source = "DPS",
        courtAppearanceId = null,
      ),
      personReference = PersonReferenceList(
        identifiers = listOf(
          PersonReference(
            type = "NOMS",
            value = request.offenderNo,
          ),
        ),
      ),
    )
    courtSentencingService.createCharge(event)
  }

  suspend fun cloneCaseToLatestBooking(offenderNo: String, dpsCourtCaseId: String) {
    val telemetryMap = mutableMapOf(
      "dpsCourtCaseId" to dpsCourtCaseId,
      "offenderNo" to offenderNo,
    )
    val response = nomisApiService.cloneCourtCase(
      offenderNo = offenderNo,
      courtCaseId = courtCaseMappingService.getMappingGivenCourtCaseId(dpsCourtCaseId = dpsCourtCaseId).nomisCourtCaseId.also {
        telemetryMap["nomisCourtCaseId"] = it.toString()
      },
    )
    courtSentencingService.tryUpdateCaseMappingsAndNotifyClonedCases(
      mappingsWrapper = response.toCourtCaseBatchMappingDto(offenderNo = offenderNo),
      offenderNo = offenderNo,
      telemetry = telemetryMap,
    )
  }

  suspend fun resynchroniseCourtCaseInNomis(offenderNo: String, courtCaseId: String): CourtCaseRepairResponse {
    val (dpsCaseId, nomisCaseId) = getCaseIds(courtCaseId)
    val dpsCourtCase = dpsApiService.getCourtCaseForReconciliation(dpsCaseId)
    val nomisCourtCase = dpsCourtCase.requiresCaseHasNoSentence().toCourtCaseRepairRequest()
    val nomisCaseResponse = nomisApiService.repairCourtCase(offenderNo, nomisCaseId, nomisCourtCase)
    courtCaseMappingService.replaceMappings(
      CourtCaseBatchMappingDto(
        courtCases = listOf(
          CourtCaseMappingDto(
            nomisCourtCaseId = nomisCaseResponse.caseId,
            dpsCourtCaseId = dpsCaseId,
            offenderNo = offenderNo,
          ),
        ),
        courtAppearances = dpsCourtCase.appearances.map { it.appearanceUuid }.zip(nomisCaseResponse.courtAppearanceIds).map { (dpsAppearanceId, nomisAppearanceId) ->
          CourtAppearanceMappingDto(
            nomisCourtAppearanceId = nomisAppearanceId,
            dpsCourtAppearanceId = dpsAppearanceId.toString(),
          )
        },
        // dps charge id is sent to NOMIS API so use what we send since it is in the same order as the response
        courtCharges = nomisCourtCase.offenderCharges.map { it.id }.zip(nomisCaseResponse.offenderChargeIds).map { (dpsChargeId, nomisChargeId) ->
          CourtChargeMappingDto(
            nomisCourtChargeId = nomisChargeId,
            dpsCourtChargeId = dpsChargeId,
          )
        },
        sentences = emptyList(),
        sentenceTerms = emptyList(),
        mappingType = CourtCaseBatchMappingDto.MappingType.DPS_CREATED,
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-repair-court-case",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisCourtCaseId" to nomisCaseId.toString(),
        "newNomisCourtCaseId" to nomisCaseResponse.caseId.toString(),
        "dpsCourtCaseId" to dpsCaseId,
      ),
      null,
    )
    return CourtCaseRepairResponse(nomisCaseId = nomisCaseResponse.caseId)
  }

  private suspend fun getCaseIds(dpsOrNomisCaseId: String): Pair<String, Long> = dpsOrNomisCaseId.toLongOrNull()?.let {
    (courtCaseMappingService.getMappingGivenNomisCourtCaseIdOrNull(it)?.dpsCourtCaseId ?: throw BadRequestException("No mapping found for $dpsOrNomisCaseId")) to it
  } ?: let {
    dpsOrNomisCaseId to (courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsOrNomisCaseId)?.nomisCourtCaseId ?: throw BadRequestException("No mapping found for $dpsOrNomisCaseId"))
  }
}

private fun ReconciliationCourtCase.requiresCaseHasNoSentence(): ReconciliationCourtCase {
  if (this.appearances.flatMap { it.charges }.any { it.sentence != null }) {
    throw ConflictException("Court case ${this.courtCaseUuid} has a sentence, only case without a sentence can be repaired in NOMIS")
  }

  return this
}
