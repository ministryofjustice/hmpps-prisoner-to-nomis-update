package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.CsipRecord

@Service
class CSIPDpsApiService(@Qualifier("csipApiWebClient") private val webClient: WebClient) {
  suspend fun getCsipReport(csipReportId: String): CsipRecord =
    webClient
      .get()
      .uri("/csip-records/{csipReportId}", csipReportId)
      .retrieve()
      .awaitBody()

  suspend fun getCSIPsForPrisoner(offenderNo: String): List<CsipRecord> =
    webClient
      .get()
      .uri("/sync/csip-records/{offenderNo}", offenderNo)
      .retrieve()
      .awaitBody()
}
