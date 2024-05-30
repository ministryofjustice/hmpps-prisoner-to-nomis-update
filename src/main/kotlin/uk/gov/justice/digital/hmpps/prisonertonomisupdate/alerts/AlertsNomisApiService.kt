package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertType

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

  suspend fun getAlertsForReconciliation(offenderNo: String): PrisonerAlertsResponse =
    webClient.get().uri(
      "/prisoners/{offenderNo}/alerts/reconciliation",
      offenderNo,
    )
      .retrieve()
      .awaitBody()

  suspend fun createAlertCode(alertCode: CreateAlertCode) {
    webClient.post()
      .uri("/alerts/codes")
      .bodyValue(alertCode)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun updateAlertCode(code: String, alertCode: UpdateAlertCode) {
    webClient.put()
      .uri("/alerts/codes/{code}", code)
      .bodyValue(alertCode)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deactivateAlertCode(code: String) {
    webClient.put()
      .uri("/alerts/codes/{code}/deactivate", code)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun reactivateAlertCode(code: String) {
    webClient.put()
      .uri("/alerts/codes/{code}/reactivate", code)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createAlertType(alertType: CreateAlertType) {
    webClient.post()
      .uri("/alerts/types")
      .bodyValue(alertType)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun updateAlertType(code: String, alertType: UpdateAlertType) {
    webClient.put()
      .uri("/alerts/types/{code}", code)
      .bodyValue(alertType)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deactivateAlertType(code: String) {
    webClient.put()
      .uri("/alerts/types/{code}/deactivate", code)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun reactivateAlertType(code: String) {
    webClient.put()
      .uri("/alerts/types/{code}/reactivate", code)
      .retrieve()
      .awaitBodilessEntity()
  }
}
