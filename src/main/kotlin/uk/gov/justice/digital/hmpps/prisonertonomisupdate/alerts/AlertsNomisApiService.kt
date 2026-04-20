package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.AlertsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAlertCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAlertType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAlertCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAlertType

@Service
class AlertsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = AlertsResourceApi(webClient)

  suspend fun createAlert(
    offenderNo: String,
    nomisAlert: CreateAlertRequest,
  ): CreateAlertResponse = api.createAlert(
    offenderNo = offenderNo,
    createAlertRequest = nomisAlert,
  ).awaitSingle()

  suspend fun updateAlert(bookingId: Long, alertSequence: Long, nomisAlert: UpdateAlertRequest): AlertResponse = api.updateAlert(
    bookingId = bookingId,
    alertSequence = alertSequence,
    updateAlertRequest = nomisAlert,
  ).awaitSingle()

  suspend fun deleteAlert(bookingId: Long, alertSequence: Long) {
    api.deleteAlert(bookingId, alertSequence).awaitSingle()
  }

  suspend fun resynchroniseAlerts(offenderNo: String, nomisAlerts: List<CreateAlertRequest>): List<CreateAlertResponse> = api.resynchroniseAlerts(
    offenderNo = offenderNo,
    createAlertRequest = nomisAlerts,
  ).awaitSingle()

  suspend fun getAlertsForReconciliation(offenderNo: String): PrisonerAlertsResponse = api.getActiveAlertsForReconciliation(offenderNo).awaitSingle()

  suspend fun createAlertCode(alertCode: CreateAlertCode) {
    api.createAlertCode(alertCode).awaitSingle()
  }

  suspend fun updateAlertCode(code: String, alertCode: UpdateAlertCode) {
    api.updateAlertCode(code, alertCode).awaitSingle()
  }

  suspend fun deactivateAlertCode(code: String) {
    api.deactivateAlertCode(code).awaitSingle()
  }

  suspend fun reactivateAlertCode(code: String) {
    api.reactivateAlertCode(code).awaitSingle()
  }

  suspend fun createAlertType(alertType: CreateAlertType) {
    api.createAlertType(alertType).awaitSingle()
  }

  suspend fun updateAlertType(code: String, alertType: UpdateAlertType) {
    api.updateAlertType(code, alertType).awaitSingle()
  }

  suspend fun deactivateAlertType(code: String) {
    api.deactivateAlertType(code).awaitSingle()
  }

  suspend fun reactivateAlertType(code: String) {
    api.reactivateAlertType(code).awaitSingle()
  }
}
