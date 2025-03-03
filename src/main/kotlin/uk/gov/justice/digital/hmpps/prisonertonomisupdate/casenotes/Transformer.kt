package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAmendment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCaseNoteRequest

fun CaseNote.toNomisCreateRequest(): CreateCaseNoteRequest = CreateCaseNoteRequest(
  caseNoteType = this.type,
  caseNoteSubType = this.subType,
  occurrenceDateTime = this.occurrenceDateTime,
  creationDateTime = this.creationDateTime,
  authorUsername = this.authorUsername,
  caseNoteText = this.text,
)

fun CaseNote.toNomisUpdateRequest(): UpdateCaseNoteRequest = UpdateCaseNoteRequest(
  text = this.text,
  amendments = this.amendments.map {
    UpdateAmendment(
      text = it.additionalNoteText,
      authorUsername = it.authorUserName,
      createdDateTime = it.creationDateTime!!,
    )
  },
)
