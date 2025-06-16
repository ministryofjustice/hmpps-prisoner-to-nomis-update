package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceRecallMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceRecallMappingsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceTermMappingDto

@Service
class CourtCaseMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: CourtCaseAllMappingDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/court-cases")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun deleteByDpsId(dpsCaseId: String) {
    webClient.delete()
      .uri(
        "/mapping/court-sentencing/court-cases/dps-court-case-id/{dpsCourtCaseId}",
        dpsCaseId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteCourtAppearanceMappingByDpsId(dpsAppearanceId: String) {
    webClient.delete()
      .uri(
        "/mapping/court-sentencing/court-appearances/dps-court-appearance-id/{dpsCourtAppearanceId}",
        dpsAppearanceId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createAppearanceMapping(request: CourtAppearanceMappingDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/court-appearances")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun createAppearanceRecallMappings(request: CourtAppearanceRecallMappingsDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/court-appearances/recall")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }
  suspend fun deleteAppearanceRecallMappings(recallId: String) {
    webClient.delete()
      .uri("/mapping/court-sentencing/court-appearances/dps-recall-id/{recallId}", recallId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getAppearanceRecallMappings(recallId: String): List<CourtAppearanceRecallMappingDto> = webClient.get()
    .uri("/mapping/court-sentencing/court-appearances/dps-recall-id/{recallId}", recallId)
    .retrieve()
    .awaitBody()

  suspend fun createChargeMapping(request: CourtChargeMappingDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/court-charges")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun createChargeBatchUpdateMapping(request: CourtChargeBatchUpdateMappingDto) {
    webClient.put()
      .uri("/mapping/court-sentencing/court-charges")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun createSentenceMapping(request: SentenceMappingDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/sentences")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }
  suspend fun createSentenceTermMapping(request: SentenceTermMappingDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/sentence-terms")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenCourtCaseIdOrNull(dpsCourtCaseId: String): CourtCaseMappingDto? = webClient.get()
    .uri("/mapping/court-sentencing/court-cases/dps-court-case-id/{dpsCourtCaseId}", dpsCourtCaseId)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenCourtCaseId(dpsCourtCaseId: String): CourtCaseMappingDto = webClient.get()
    .uri("/mapping/court-sentencing/court-cases/dps-court-case-id/{dpsCourtCaseId}", dpsCourtCaseId)
    .retrieve()
    .awaitBody()

  suspend fun getMappingGivenNomisCourtCaseId(nomisCourtCaseId: Long): CourtCaseMappingDto = webClient.get()
    .uri("/mapping/court-sentencing/court-cases/nomis-court-case-id/{nomisCourtCaseId}", nomisCourtCaseId)
    .retrieve()
    .awaitBody()

  suspend fun getMappingGivenCourtAppearanceIdOrNull(dpsCourtAppearanceId: String): CourtAppearanceMappingDto? = webClient.get()
    .uri("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/{dpsCourtAppearanceId}", dpsCourtAppearanceId)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenCourtAppearanceId(dpsCourtAppearanceId: String): CourtAppearanceMappingDto = webClient.get()
    .uri("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/{dpsCourtAppearanceId}", dpsCourtAppearanceId)
    .retrieve()
    .awaitBody()

  suspend fun getMappingGivenCourtChargeIdOrNull(dpsCourtChargeId: String): CourtChargeMappingDto? = webClient.get()
    .uri("/mapping/court-sentencing/court-charges/dps-court-charge-id/{dpsCourtChargeId}", dpsCourtChargeId)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenCourtChargeId(dpsCourtChargeId: String): CourtChargeMappingDto = webClient.get()
    .uri("/mapping/court-sentencing/court-charges/dps-court-charge-id/{dpsCourtChargeId}", dpsCourtChargeId)
    .retrieve()
    .awaitBody()

  suspend fun getMappingGivenSentenceIdOrNull(dpsSentenceId: String): SentenceMappingDto? = webClient.get()
    .uri("/mapping/court-sentencing/sentences/dps-sentence-id/{dpsSentenceId}", dpsSentenceId)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenSentenceId(dpsSentenceId: String): SentenceMappingDto = webClient.get()
    .uri("/mapping/court-sentencing/sentences/dps-sentence-id/{dpsSentenceId}", dpsSentenceId)
    .retrieve()
    .awaitBody()

  suspend fun getMappingsGivenSentenceIds(dpsSentenceIds: List<String>): List<SentenceMappingDto> = webClient.post()
    .uri("/mapping/court-sentencing/sentences/dps-sentence-ids/get-list")
    .bodyValue(dpsSentenceIds)
    .retrieve()
    .awaitBody()

  suspend fun deleteSentenceMappingByDpsId(dpsSentenceId: String) {
    webClient.delete()
      .uri(
        "/mapping/court-sentencing/sentences/dps-sentence-id/{dpsSentenceId}",
        dpsSentenceId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getMappingGivenSentenceTermIdOrNull(dpsTermId: String): SentenceTermMappingDto? = webClient.get()
    .uri("/mapping/court-sentencing/sentence-terms/dps-term-id/{dpsTermId}", dpsTermId)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenSentenceTermId(dpsTermId: String): SentenceTermMappingDto = webClient.get()
    .uri("/mapping/court-sentencing/sentence-terms/dps-term-id/{dpsTermId}", dpsTermId)
    .retrieve()
    .awaitBody()

  suspend fun deleteSentenceTermMappingByDpsId(dpsTermId: String) {
    webClient.delete()
      .uri(
        "/mapping/court-sentencing/sentence-terms/dps-term-id/{dpsTermId}",
        dpsTermId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }
}
