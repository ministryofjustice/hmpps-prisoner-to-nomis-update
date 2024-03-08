package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class AlertsService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: AlertsDpsApiService,
  private val nomisApiService: AlertsNomisApiService,
  private val mappingApiService: AlertsMappingApiService,
  private val alertsRetryQueueService: AlertsRetryQueueService,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  suspend fun createAlert(alertEvent: AlertEvent) {
    val dpsAlertId = alertEvent.additionalInformation.alertUuid
    val offenderNo = alertEvent.additionalInformation.prisonNumber
    val telemetryMap = mapOf(
      "dpsAlertId" to dpsAlertId,
      "offenderNo" to offenderNo,
      "alertCode" to alertEvent.additionalInformation.alertCode,
    )

    if (alertEvent.wasCreatedInDPS()) {
      synchronise {
        name = "alert"
        telemetryClient = this@AlertsService.telemetryClient
        retryQueueService = alertsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getOrNullByDpsId(dpsAlertId)
        }
        transform {
          dpsApiService.getAlert(dpsAlertId)
            .let { dpsAlert ->
              nomisApiService.createAlert(offenderNo, dpsAlert.toNomisCreateRequest()).let { nomisAlert ->
                AlertMappingDto(
                  dpsAlertId = dpsAlertId,
                  nomisBookingId = nomisAlert.bookingId,
                  nomisAlertSequence = nomisAlert.alertSequence,
                  mappingType = AlertMappingDto.MappingType.DPS_CREATED,
                )
              }
            }
        }
        saveMapping { mappingApiService.createMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("alert-create-ignored", telemetryMap)
    }
  }

  suspend fun createMapping(message: CreateMappingRetryMessage<AlertMappingDto>) =
    mappingApiService.createMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "alert-create-success",
        message.telemetryAttributes,
        null,
      )
    }

  override suspend fun retryCreateMapping(message: String) = createMapping(message.fromJson())

  fun updateAlert(alertEvent: AlertEvent) {
    TODO("Not yet implemented")
  }

  fun deleteAlert(alertEvent: AlertEvent) {
    TODO("Not yet implemented")
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
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
