package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto

@Service
class CaseNotesMappingApiService(@Qualifier("mappingWebClient") private val webClient: WebClient) {
  suspend fun getOrNullByDpsId(caseNoteId: String): CaseNoteMappingDto? = webClient.get()
    .uri(
      "/mapping/casenotes/dps-casenote-id/{casenoteId}",
      caseNoteId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

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
