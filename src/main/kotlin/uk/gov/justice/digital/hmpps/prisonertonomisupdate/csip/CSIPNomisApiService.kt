package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity

@Service
class CSIPNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun deleteCsipReport(csipReportId: Long) {
    webClient.delete()
      .uri("/csip/{csipReportId}", csipReportId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
