package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertRequest

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

  suspend fun updateAlert(bookingId: Long, alertSequence: Long, nomisAlert: UpdateAlertRequest): AlertResponse = webClient.put().uri(
    "/prisoners/booking-id/{bookingId}/alerts/{alertSequence}",
    bookingId,
    alertSequence,
  )
    .bodyValue(nomisAlert)
    .retrieve()
    .awaitBody()

  suspend fun deleteAlert(bookingId: Long, alertSequence: Long) {
    webClient.delete().uri(
      "/prisoners/booking-id/{bookingId}/alerts/{alertSequence}",
      bookingId,
      alertSequence,
    )
      .retrieve()
      .awaitBodilessEntity()
  }
}
