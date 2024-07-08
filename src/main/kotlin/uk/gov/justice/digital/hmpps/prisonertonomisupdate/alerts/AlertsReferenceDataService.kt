package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertType

@Service
class AlertsReferenceDataService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: AlertsDpsApiService,
  private val nomisApiService: AlertsNomisApiService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun createAlertCode(event: AlertReferenceDataEvent) {
    log.info("Creating an alert code: $event")
    val alertCode = dpsApiService.getAlertCode(event.additionalInformation.alertCode)
    nomisApiService.createAlertCode(
      CreateAlertCode(
        code = alertCode.code,
        description = alertCode.description,
        listSequence = alertCode.listSequence,
        typeCode = alertCode.alertTypeCode,
      ),
    )
    telemetryClient.trackEvent("alert-code-create-success", mapOf("alert-code" to alertCode.code))
  }

  suspend fun updateAlertCode(event: AlertReferenceDataEvent) {
    log.info("Updating an alert code: $event")
    val alertCode = dpsApiService.getAlertCode(event.additionalInformation.alertCode)
    nomisApiService.updateAlertCode(
      code = alertCode.code,
      alertCode = UpdateAlertCode(
        description = alertCode.description,
      ),
    )
    telemetryClient.trackEvent("alert-code-update-success", mapOf("alert-code" to alertCode.code))
  }

  suspend fun deactivatingAlertCode(event: AlertReferenceDataEvent) {
    log.info("Deactivating an alert code: $event")
    val alertCode = dpsApiService.getAlertCode(event.additionalInformation.alertCode)
    if (alertCode.isActive) {
      telemetryClient.trackEvent("alert-code-deactivate-ignored", mapOf("alert-code" to alertCode.code))
    } else {
      nomisApiService.deactivateAlertCode(code = alertCode.code)
      telemetryClient.trackEvent("alert-code-deactivate-success", mapOf("alert-code" to alertCode.code))
    }
  }

  suspend fun reactivatingAlertCode(event: AlertReferenceDataEvent) {
    log.info("Reactivating an alert code: $event")
    val alertCode = dpsApiService.getAlertCode(event.additionalInformation.alertCode)
    if (!alertCode.isActive) {
      telemetryClient.trackEvent("alert-code-reactivate-ignored", mapOf("alert-code" to alertCode.code))
    } else {
      nomisApiService.reactivateAlertCode(code = alertCode.code)
      telemetryClient.trackEvent("alert-code-reactivate-success", mapOf("alert-code" to alertCode.code))
    }
  }

  suspend fun createAlertType(event: AlertReferenceDataEvent) {
    log.info("Creating an alert type: $event")
    val alertType = dpsApiService.getAlertType(event.additionalInformation.alertCode)
    nomisApiService.createAlertType(
      CreateAlertType(
        code = alertType.code,
        description = alertType.description,
        listSequence = alertType.listSequence,
      ),
    )
    telemetryClient.trackEvent("alert-type-create-success", mapOf("alert-type" to alertType.code))
  }

  suspend fun updateAlertType(event: AlertReferenceDataEvent) {
    log.info("Updating an alert type: $event")
    val alertType = dpsApiService.getAlertType(event.additionalInformation.alertCode)
    nomisApiService.updateAlertType(
      code = alertType.code,
      alertType = UpdateAlertType(
        description = alertType.description,
      ),
    )
    telemetryClient.trackEvent("alert-type-update-success", mapOf("alert-type" to alertType.code))
  }

  suspend fun deactivatingAlertType(event: AlertReferenceDataEvent) {
    log.info("Deactivating an alert type: $event")
    val alertType = dpsApiService.getAlertType(event.additionalInformation.alertCode)
    if (alertType.isActive) {
      telemetryClient.trackEvent("alert-type-deactivate-ignored", mapOf("alert-type" to alertType.code))
    } else {
      nomisApiService.deactivateAlertType(code = alertType.code)
      telemetryClient.trackEvent("alert-type-deactivate-success", mapOf("alert-type" to alertType.code))
    }
  }

  suspend fun reactivatingAlertType(event: AlertReferenceDataEvent) {
    log.info("Reactivating an alert type: $event")
    val alertType = dpsApiService.getAlertType(event.additionalInformation.alertCode)
    if (!alertType.isActive) {
      telemetryClient.trackEvent("alert-type-reactivate-ignored", mapOf("alert-type" to alertType.code))
    } else {
      nomisApiService.reactivateAlertType(code = alertType.code)
      telemetryClient.trackEvent("alert-type-reactivate-success", mapOf("alert-type" to alertType.code))
    }
  }
}

data class AlertReferenceDataEvent(
  val description: String?,
  val eventType: String,
  val additionalInformation: AlertReferenceDataAdditionalInformation,
)

data class AlertReferenceDataAdditionalInformation(
  val alertCode: String,
)
