package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAmendment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCaseNoteRequest
import java.time.format.DateTimeFormatter.ISO_DATE

fun CaseNote.toNomisCreateRequest(): CreateCaseNoteRequest =
  CreateCaseNoteRequest(
    caseNoteType = this.type,
    caseNoteSubType = this.subType,
    occurrenceDateTime = this.occurrenceDateTime.toString(),
    authorUsername = this.authorName,
    caseNoteText = this.text,
  )

fun CaseNote.toNomisUpdateRequest(): UpdateCaseNoteRequest =
  UpdateCaseNoteRequest(
    text = this.text,
    amendments = this.amendments.map {
      UpdateAmendment(
        text = it.additionalNoteText,
        authorUsername = it.authorUserName,
        createdDateTime = it.creationDateTime?.format(ISO_DATE) ?: "",
      )
    },
  )
