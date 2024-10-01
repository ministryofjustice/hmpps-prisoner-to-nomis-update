package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPResponse

@Service
class CSIPNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun upsertCsipReport(nomisCSIPReport: UpsertCSIPRequest): UpsertCSIPResponse =
    webClient.put()
      .uri("/csip")
      .bodyValue(nomisCSIPReport)
      .retrieve()
      .awaitBody()

/*
  wip
  suspend fun getCSIPForReconciliation(csipReportId: Long) {
    webClient.get()
      .uri("/csip/{csipReportId}", csipReportId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getCSIPIds(
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<CSIPIdResponse> =
    webClient
      .get()
      .uri {
        it.path("/csip/ids")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<CSIPIdResponse>>())
      .awaitSingle()


 */
  suspend fun deleteCsipReport(csipReportId: Long) {
    webClient.delete()
      .uri("/csip/{csipReportId}", csipReportId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
