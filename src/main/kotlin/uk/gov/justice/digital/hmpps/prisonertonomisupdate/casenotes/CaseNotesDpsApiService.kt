package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote

@Service
class CaseNotesDpsApiService(@Qualifier("caseNotesApiWebClient") private val webClient: WebClient) {
  suspend fun getCaseNote(caseNoteId: String): CaseNote =
    webClient
      .get()
      .uri("/case-notes/case-note-id/{caseNoteIdentifier}", caseNoteId)
      .retrieve()
      .awaitBody()

  suspend fun getCaseNotesForPrisoner(offenderIdentifier: String): List<CaseNote> =
    webClient
      .get()
      .uri("/case-notes/{offenderIdentifier}", offenderIdentifier)
      .retrieve()
      .awaitBody()

  // TODO: These endpoints are 'aspirational' - what I would like the api to provide.
}
