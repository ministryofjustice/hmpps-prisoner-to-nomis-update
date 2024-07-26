package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AmendCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCaseNoteRequest

fun CaseNote.toNomisCreateRequest(): CreateCaseNoteRequest =
  CreateCaseNoteRequest(
    caseNoteType = this.type,
    caseNoteSubType = this.subType,
    occurrenceDateTime = this.occurrenceDateTime.toString(),
    authorUsername = this.authorName,
    caseNoteText = this.text,
  )

fun CaseNote.toNomisAmendRequest(): AmendCaseNoteRequest =
  AmendCaseNoteRequest(
    caseNoteType = this.type,
    caseNoteSubType = this.subType,
    occurrenceDateTime = this.occurrenceDateTime.toString(),
    authorUsername = this.authorName,
    caseNoteText = this.text,
  )
