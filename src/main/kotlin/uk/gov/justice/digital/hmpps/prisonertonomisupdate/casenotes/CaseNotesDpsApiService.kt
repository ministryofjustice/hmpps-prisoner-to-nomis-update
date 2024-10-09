package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference
import java.net.URI

@Service
class CaseNotesDpsApiService(
  @Qualifier("caseNotesApiWebClient") private val webClient: WebClient,
  @Value("\${hmpps.web-client.casenotes.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value("\${hmpps.web-client.casenotes.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

  suspend fun getCaseNote(offenderNo: String, caseNoteId: String): CaseNote {
    lateinit var url: URI
    return webClient
      .get()
      .uri {
        url = it.path("/case-notes/{offenderNo}/{caseNoteIdentifier}")
          .build(offenderNo, caseNoteId)
        url
      }
      .retrieve()
      .bodyToMono(CaseNote::class.java)
      .retryWhen(backoffSpec.withRetryContext(Context.of("api", "casenotes-api.getCaseNote", "url", url.path)))
      .awaitSingle()
  }

  suspend fun getCaseNotesForPrisoner(
    offenderIdentifier: String,
    pageNumber: Long,
    pageSize: Long,
  ): Page<CaseNote> {
    lateinit var url: URI
    return webClient
      .get()
      .uri {
        url = it.path("/case-notes/{offenderIdentifier}")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build(offenderIdentifier)
        url
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<CaseNote>>())
      .retryWhen(backoffSpec.withRetryContext(Context.of("api", "casenotes-api.getCaseNotesForPrisoner", "url", url.path)))
      .awaitSingle()
  }
}
