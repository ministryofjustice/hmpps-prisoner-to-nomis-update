package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import java.time.LocalDateTime

@Service
class CourtCaseMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: CourtCaseMappingDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/court-case")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenCourtCaseIdOrNull(courtCaseId: String): CourtCaseMappingDto? =
    webClient.get()
      .uri("/mapping/court-sentencing/court-case/court-case-id/{courtCaseId}", courtCaseId)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenCourtCaseId(courtCaseId: String): CourtCaseMappingDto =
    webClient.get()
      .uri("/mapping/court-sentencing/court-case/court-case-id/{courtCaseId}", courtCaseId)
      .retrieve()
      .awaitBody()

  suspend fun deleteMappingGivenCourtCaseId(courtCaseId: String): Unit =
    webClient.delete()
      .uri("/mapping/court-sentencing/court-case/court-case-id/{courtCaseId}", courtCaseId)
      .retrieve()
      .awaitBody()
}

data class CourtCaseMappingDto(
  val nomisCourtCaseId: Long,
  val courtCaseId: String,
  val courtAppearanceMappings: List<CourtAppearanceMappingDto> = listOf(),
  val label: String? = null,
  val mappingType: String = "COURT_CASE_CREATED",
  val whenCreated: LocalDateTime? = null,
)

data class CourtAppearanceMappingDto(
  val nomisCourtAppearanceId: Long,
  val courtAppearanceId: String,
  val offenderChargeMappings: List<OffenderChargeMappingDto> = listOf(),
  val mappingType: String,
  val whenCreated: LocalDateTime? = null,
)

data class OffenderChargeMappingDto(
  val nomisOffenderChargeId: Long,
  val mappingType: String,
  val whenCreated: LocalDateTime? = null,
)
