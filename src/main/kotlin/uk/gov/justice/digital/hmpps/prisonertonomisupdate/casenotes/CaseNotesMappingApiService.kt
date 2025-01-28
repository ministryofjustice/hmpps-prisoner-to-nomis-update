package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AllPrisonerCaseNoteMappingsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CaseNotesMappingApiService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
  @Value("\${hmpps.web-client.case-notes-mapping.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value("\${hmpps.web-client.case-notes-mapping.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

  suspend fun getOrNullByDpsId(caseNoteId: String): List<CaseNoteMappingDto>? = webClient.get()
    .uri(
      "/mapping/casenotes/dps-casenote-id/{casenoteId}/all",
      caseNoteId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByPrisoner(offenderNo: String): AllPrisonerCaseNoteMappingsDto = webClient.get()
    .uri("/mapping/casenotes/{offenderNo}/all", offenderNo)
    .retrieve()
    .bodyToMono(AllPrisonerCaseNoteMappingsDto::class.java)
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "case-notes-mapping-api", "path", "/mapping/casenotes/{offenderNo}/all", "offenderNo", offenderNo)))
    .awaitSingle()

  suspend fun createMapping(caseNoteMappingDto: CaseNoteMappingDto) {
    webClient.post()
      .uri("/mapping/casenotes")
      .bodyValue(caseNoteMappingDto)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteByDpsId(caseNoteId: String) {
    webClient.delete()
      .uri(
        "/mapping/casenotes/dps-casenote-id/{casenoteId}",
        caseNoteId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }
}
