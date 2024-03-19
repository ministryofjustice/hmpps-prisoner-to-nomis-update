package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AlertMappingDto

@Service
class AlertsMappingApiService(@Qualifier("mappingWebClient") private val webClient: WebClient) {
  suspend fun getOrNullByDpsId(alertId: String): AlertMappingDto? = webClient.get()
    .uri(
      "/mapping/alerts/dps-alert-id/{alertId}",
      alertId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createMapping(alertMappingDto: AlertMappingDto) {
    webClient.post()
      .uri("/mapping/alerts")
      .bodyValue(alertMappingDto)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteByDpsId(alertId: String) {
    webClient.delete()
      .uri(
        "/mapping/alerts/dps-alert-id/{alertId}",
        alertId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }
}
