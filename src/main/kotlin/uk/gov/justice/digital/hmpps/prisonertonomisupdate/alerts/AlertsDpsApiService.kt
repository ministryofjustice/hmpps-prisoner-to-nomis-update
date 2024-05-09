package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference

@Service
class AlertsDpsApiService(@Qualifier("alertsApiWebClient") private val webClient: WebClient) {
  suspend fun getAlert(alertId: String): Alert =
    webClient
      .get()
      .uri("/alerts/{alertId}", alertId)
      .retrieve()
      .awaitBody()

  suspend fun getActiveAlertsForPrisoner(offenderNo: String): List<Alert> =
    webClient
      .get()
      .uri {
        it.path("/prisoners/{offenderNo}/alerts")
          .queryParam("isActive", true)
          .queryParam("page", 0)
          .queryParam("size", 1000)
          .build(offenderNo)
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<Alert>>())
      .awaitSingle().let {
        // since a person can not typically have multiple active alerts the page size needs to be as big as the number of different alert codes (213)
        // so give it a reasonable headroom for growth and in the unlikely event of exceeding the size than throw an error and we can increase size with a code change
        if (it.totalElements != it.numberOfElements.toLong()) throw IllegalStateException("Page size of 1000 for /prisoners/{offenderNo}/alerts not big enough ${it.totalElements} not equal to ${it.numberOfElements}")
        it.content
      }
}
