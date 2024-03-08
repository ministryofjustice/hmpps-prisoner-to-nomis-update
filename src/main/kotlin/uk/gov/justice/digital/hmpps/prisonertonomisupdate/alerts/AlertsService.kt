package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class AlertsService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: AlertsDpsApiService,
  private val nomisApiService: AlertsNomisApiService,
  private val mappingApiService: AlertsMappingApiService,
) : CreateMappingRetryable {
  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }

  suspend fun createAlert(alertEvent: AlertEvent) {
    val dpsAlertId = alertEvent.additionalInformation.alertUuid
    val offenderNo = alertEvent.additionalInformation.prisonNumber
    val telemetryMap = mapOf(
      "dpsAlertId" to dpsAlertId,
      "offenderNo" to offenderNo,
      "alertCode" to alertEvent.additionalInformation.alertCode,
    )

    if (alertEvent.wasCreatedInDPS()) {
      val dpsAlert = dpsApiService.getAlert(dpsAlertId)
      val nomisAlert = nomisApiService.createAlert(offenderNo, dpsAlert.toNomisCreateRequest())
      val mapping = AlertMappingDto(
        dpsAlertId = dpsAlertId,
        nomisBookingId = nomisAlert.bookingId,
        nomisAlertSequence = nomisAlert.alertSequence,
        mappingType = AlertMappingDto.MappingType.DPS_CREATED,
      )
      mappingApiService.createMapping(mapping)
      telemetryClient.trackEvent(
        "alert-create-success",
        telemetryMap + mapOf("nomisBookingId" to mapping.nomisBookingId.toString(), "nomisAlertSequence" to mapping.nomisAlertSequence.toString()),
      )
    } else {
      telemetryClient.trackEvent("alert-create-ignored", telemetryMap)
    }
  }

  fun updateAlert(alertEvent: AlertEvent) {
    TODO("Not yet implemented")
  }

  fun deleteAlert(alertEvent: AlertEvent) {
    TODO("Not yet implemented")
  }
}

data class AlertEvent(
  val description: String?,
  val eventType: String,
  val additionalInformation: AlertAdditionalInformation,
)

data class AlertAdditionalInformation(
  val url: String,
  val alertUuid: String,
  val prisonNumber: String,
  val alertCode: String,
  val source: AlertSource,
)

enum class AlertSource {
  ALERTS_SERVICE,
  NOMIS,
  MIGRATION,
}

fun AlertEvent.wasCreatedInDPS() = this.additionalInformation.source == AlertSource.ALERTS_SERVICE
