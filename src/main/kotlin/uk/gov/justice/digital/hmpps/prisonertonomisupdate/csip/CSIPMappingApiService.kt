package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFullMappingDto

@Service
class CSIPMappingApiService(@Qualifier("mappingWebClient") private val webClient: WebClient) {
  suspend fun getOrNullByDpsId(dpsCsipReportId: String): CSIPFullMappingDto? = webClient.get()
    .uri(
      "/mapping/csip/dps-csip-id/{dpsCsipReportId}/all",
      dpsCsipReportId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun deleteByDpsId(dpsCsipReportId: String) {
    webClient.delete()
      .uri("/mapping/csip/dps-csip-id/{dpsCsipReportId}/all", dpsCsipReportId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteChildMappingsByDpsId(dpsCsipReportId: String) {
    webClient.delete()
      .uri("/mapping/csip/dps-csip-id/{dpsCsipReportId}/children", dpsCsipReportId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createMapping(csipFullMappingDto: CSIPFullMappingDto) {
    webClient.post()
      .uri("/mapping/csip/all")
      .bodyValue(csipFullMappingDto)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createChildMappings(csipFullMappingDto: CSIPFullMappingDto) {
    webClient.post()
      .uri("/mapping/csip/children/all")
      .bodyValue(csipFullMappingDto)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }
}
