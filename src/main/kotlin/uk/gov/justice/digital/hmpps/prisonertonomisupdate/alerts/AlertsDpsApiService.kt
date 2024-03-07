package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.Alert

@Service
class AlertsDpsApiService(@Qualifier("alertsApiWebClient") private val webClient: WebClient) {
  suspend fun getAlert(alertId: String): Alert =
    webClient
      .get()
      .uri("/alerts/{alertId}", alertId)
      .retrieve()
      .awaitBody()
}
