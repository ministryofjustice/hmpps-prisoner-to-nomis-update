package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.net.URI

@Service
class CaseNotesNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  @Value("\${hmpps.web-client.casenotes-nomis.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value("\${hmpps.web-client.casenotes-nomis.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

  suspend fun getCaseNote(caseNoteId: Long): CaseNoteResponse = webClient.get().uri(
    "/casenotes/{caseNoteId}",
    caseNoteId,
  )
    .retrieve()
    .awaitBody()

  suspend fun createCaseNote(offenderNo: String, nomisCaseNote: CreateCaseNoteRequest): CreateCaseNoteResponse = webClient.post()
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

  suspend fun getCaseNotesForPrisoner(offenderNo: String): PrisonerCaseNotesResponse {
    lateinit var url: URI
    return webClient.get()
      .uri {
        url = it.path("/prisoners/{offenderNo}/casenotes")
          .build(offenderNo)
        url
      }
      .retrieve()
      .bodyToMono(PrisonerCaseNotesResponse::class.java)
      .retryWhen(backoffSpec.withRetryContext(Context.of("api", "casenotes-nomis-api", "url", url.path)))
      .awaitSingle()
  }
}
