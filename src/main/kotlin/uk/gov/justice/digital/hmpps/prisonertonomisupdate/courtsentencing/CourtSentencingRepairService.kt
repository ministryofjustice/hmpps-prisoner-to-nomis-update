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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DpsCourtCaseBatchMappingDto
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
    val dpsCourtCase = dpsApiService.getCourtCaseForReconciliation(dpsCaseId).requiresCaseHasNoSentence()
    courtCaseMappingService.deleteMappingsByDpsIds(
      DpsCourtCaseBatchMappingDto(
        courtCases = listOf(dpsCaseId),
        courtAppearances = dpsCourtCase.appearances.map { it.appearanceUuid.toString() },
        courtCharges = dpsCourtCase.appearances.flatMap { it.charges }.map { it.chargeUuid.toString() }.distinct(),
        sentences = emptyList(),
        sentenceTerms = emptyList(),
      ),
    )
    val nomisCourtCase = dpsCourtCase.toCourtCaseRepairRequest()
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

  suspend fun resynchroniseSentenceInsertToNomis(offenderNo: String, courtCaseId: String, courtAppearanceId: String, sentenceId: String) {
    courtSentencingService.createSentence(
      createEvent = CourtSentencingService.SentenceCreatedEvent(
        personReference = PersonReferenceList(
          identifiers = listOf(
            PersonReference(
              type = "NOMS",
              value = offenderNo,
            ),
          ),
        ),
        additionalInformation = CourtSentencingService.SentenceAdditionalInformation(
          courtCaseId = courtCaseId,
          courtAppearanceId = courtAppearanceId,
          sentenceId = sentenceId,
          source = "DPS",
        ),
      ),
    )

    // will need to use operation id in appinsights to see created nomis ids
    telemetryClient.trackEvent(
      "court-sentencing-repair-sentence-inserted",
      mapOf(
        "offenderNo" to offenderNo,
        "dpsCourtCaseId" to courtCaseId,
        "dpsCourtAppearanceId" to courtAppearanceId,
        "dpsSentenceId" to sentenceId,
      ),
      null,
    )
  }

  suspend fun resynchroniseSentenceUpdateToNomis(offenderNo: String, courtCaseId: String, courtAppearanceId: String, sentenceId: String) {
    courtSentencingService.updateSentence(
      createEvent = CourtSentencingService.SentenceCreatedEvent(
        personReference = PersonReferenceList(
          identifiers = listOf(
            PersonReference(
              type = "NOMS",
              value = offenderNo,
            ),
          ),
        ),
        additionalInformation = CourtSentencingService.SentenceAdditionalInformation(
          courtCaseId = courtCaseId,
          courtAppearanceId = courtAppearanceId,
          sentenceId = sentenceId,
          source = "DPS",
        ),
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-repair-sentence-updated",
      mapOf(
        "offenderNo" to offenderNo,
        "dpsCourtCaseId" to courtCaseId,
        "dpsSentenceId" to sentenceId,
        "dpsCourtAppearanceId" to courtAppearanceId,
      ),
      null,
    )
  }

  suspend fun resynchroniseChargeUpdateToNomis(offenderNo: String, courtCaseId: String, courtAppearanceId: String, chargeId: String) {
    courtSentencingService.updateCharge(
      createEvent = CourtSentencingService.CourtChargeCreatedEvent(
        personReference = PersonReferenceList(
          identifiers = listOf(
            PersonReference(
              type = "NOMS",
              value = offenderNo,
            ),
          ),
        ),
        additionalInformation = CourtSentencingService.CourtChargeAdditionalInformation(
          courtCaseId = courtCaseId,
          courtAppearanceId = courtAppearanceId,
          courtChargeId = chargeId,
          source = "DPS",
        ),
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-repair-charge-updated",
      mapOf(
        "offenderNo" to offenderNo,
        "dpsCourtCaseId" to courtCaseId,
        "dpsChargeId" to chargeId,
        "dpsCourtAppearanceId" to courtAppearanceId,
      ),
      null,
    )
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
