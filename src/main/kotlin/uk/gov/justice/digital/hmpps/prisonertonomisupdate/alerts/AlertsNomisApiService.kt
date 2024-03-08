package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertResponse

@Service
class AlertsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun createAlert(offenderNo: String, nomisAlert: CreateAlertRequest): CreateAlertResponse = webClient.post()
    .uri(
      "/prisoners/{offenderNo}/alerts",
      offenderNo,
    )
    .bodyValue(nomisAlert)
    .retrieve()
    .awaitBody()
}
