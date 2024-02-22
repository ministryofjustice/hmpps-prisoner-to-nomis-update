package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseMappingDto

@Service
class CourtCaseMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: CourtCaseMappingDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/court-cases")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId: String): CourtCaseMappingDto? =
    webClient.get()
      .uri("/mapping/court-sentencing/court-cases/dps-court-case-id/{dpsCourtCaseId}", dpsCourtCaseId)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenCourtCaseId(dpsCourtCaseId: String): CourtCaseMappingDto =
    webClient.get()
      .uri("/mapping/court-sentencing/court-cases/dps-court-case-id/{dpsCourtCaseId}", dpsCourtCaseId)
      .retrieve()
      .awaitBody()
}
