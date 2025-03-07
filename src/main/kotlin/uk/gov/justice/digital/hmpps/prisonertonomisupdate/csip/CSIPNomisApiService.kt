package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCSIPsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCSIPRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCSIPResponse

@Service
class CSIPNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun upsertCsipReport(nomisCSIPReport: UpsertCSIPRequest): UpsertCSIPResponse = webClient.put()
    .uri("/csip")
    .bodyValue(nomisCSIPReport)
    .retrieve()
    .awaitBody()

  suspend fun deleteCsipReport(csipReportId: Long) {
    webClient.delete()
      .uri("/csip/{csipReportId}", csipReportId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getCSIPsForReconciliation(offenderNo: String): PrisonerCSIPsResponse = webClient.get().uri("/prisoners/{offenderNo}/csip/reconciliation", offenderNo)
    .retrieve()
    .awaitBody()
}
