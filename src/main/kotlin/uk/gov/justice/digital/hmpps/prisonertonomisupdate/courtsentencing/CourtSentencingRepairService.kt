package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.BadRequestException
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
    val nomisCourtCase = dpsApiService.getCourtCaseForReconciliation(dpsCaseId).toCourtCaseRepairRequest()

    telemetryClient.trackEvent(
      "court-sentencing-repair-court-case",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisCourtCaseId" to nomisCaseId.toString(),
        "dpsCourtCaseId" to dpsCaseId,
      ),
      null,
    )
    return CourtCaseRepairResponse(nomisCaseId = 0)
  }

  private suspend fun getCaseIds(dpsOrNomisCaseId: String): Pair<String, Long> = dpsOrNomisCaseId.toLongOrNull()?.let {
    (courtCaseMappingService.getMappingGivenNomisCourtCaseIdOrNull(it)?.dpsCourtCaseId ?: throw BadRequestException("No mapping found for $dpsOrNomisCaseId")) to it
  } ?: let {
    dpsOrNomisCaseId to (courtCaseMappingService.getMappingGivenCourtCaseIdOrNull(dpsOrNomisCaseId)?.nomisCourtCaseId ?: throw BadRequestException("No mapping found for $dpsOrNomisCaseId"))
  }
}
