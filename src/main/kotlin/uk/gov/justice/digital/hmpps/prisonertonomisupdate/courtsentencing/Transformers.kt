package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifier
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseRepairRequest

fun ReconciliationCourtCase.toCourtCaseRepairRequest(): CourtCaseRepairRequest = CourtCaseRepairRequest(
  startDate = this.appearances.minOf { it.appearanceDate },
  legalCaseType = "NE",
  courtId = this.appearances.minBy { it.appearanceDate }.courtCode,
  status = if (this.active) {
    "A"
  } else {
    "I"
  },
  courtAppearances = emptyList(),
  offenderCharges = emptyList(),
  caseReferences = CaseIdentifierRequest(
    caseIdentifiers = this.courtCaseLegacyData!!.caseReferences.map {
      CaseIdentifier(
        reference = it.offenderCaseReference,
        createdDate = it.updatedDate,
      )
    },
  ),
)
