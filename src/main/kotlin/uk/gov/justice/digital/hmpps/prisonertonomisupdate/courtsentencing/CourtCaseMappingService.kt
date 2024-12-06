package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeBatchUpdateMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto

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

  suspend fun getMappingGivenCourtAppearanceIdOrNull(dpsCourtAppearanceId: String): CourtAppearanceMappingDto? =
    webClient.get()
      .uri("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/{dpsCourtAppearanceId}", dpsCourtAppearanceId)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenCourtAppearanceId(dpsCourtAppearanceId: String): CourtAppearanceMappingDto =
    webClient.get()
      .uri("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/{dpsCourtAppearanceId}", dpsCourtAppearanceId)
      .retrieve()
      .awaitBody()

  suspend fun getMappingGivenCourtChargeIdOrNull(dpsCourtChargeId: String): CourtChargeMappingDto? =
    webClient.get()
      .uri("/mapping/court-sentencing/court-charges/dps-court-charge-id/{dpsCourtChargeId}", dpsCourtChargeId)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenCourtChargeId(dpsCourtChargeId: String): CourtChargeMappingDto =
    webClient.get()
      .uri("/mapping/court-sentencing/court-charges/dps-court-charge-id/{dpsCourtChargeId}", dpsCourtChargeId)
      .retrieve()
      .awaitBody()
}
