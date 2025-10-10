package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList

@Service
class CourtSentencingRepairService(
  private val courtSentencingService: CourtSentencingService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val courtCaseMappingService: CourtCaseMappingService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

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

  fun resynchroniseCourtCaseInNomis(@Suppress("unused") offenderNo: String, @Suppress("unused") courtCaseId: String): CourtCaseRepairResponse = CourtCaseRepairResponse(nomisCaseId = 0)
}
