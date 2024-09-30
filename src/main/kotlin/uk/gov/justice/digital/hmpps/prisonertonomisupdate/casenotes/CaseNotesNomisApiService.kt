package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCaseNoteRequest

@Service
class CaseNotesNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getCaseNote(caseNoteId: Long): CaseNoteResponse =
    webClient.get().uri(
      "/casenotes/{caseNoteId}",
      caseNoteId,
    )
      .retrieve()
      .awaitBody()

  suspend fun createCaseNote(offenderNo: String, nomisCaseNote: CreateCaseNoteRequest): CreateCaseNoteResponse =
    webClient.post()
      .uri(
        "/prisoners/{offenderNo}/casenotes",
        offenderNo,
      )
      .bodyValue(nomisCaseNote)
      .retrieve()
      .awaitBody()

  suspend fun updateCaseNote(caseNoteId: Long, nomisCaseNote: UpdateCaseNoteRequest) {
    webClient.put().uri(
      "/casenotes/{caseNoteId}",
      caseNoteId,
    )
      .bodyValue(nomisCaseNote)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteCaseNote(caseNoteId: Long) {
    webClient.delete().uri(
      "/casenotes/{caseNoteId}",
      caseNoteId,
    )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getCaseNotesForPrisoner(offenderNo: String): PrisonerCaseNotesResponse =
    webClient.get().uri(
      "/prisoners/{offenderNo}/casenotes",
      offenderNo,
    )
      .retrieve()
      .awaitBody()
}
