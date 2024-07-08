package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun createAlert(alertEvent: AlertEvent) {
    val dpsAlertId = alertEvent.additionalInformation.alertUuid
    val offenderNo = requireNotNull(alertEvent.personReference.findNomsNumber())
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
                  offenderNo = offenderNo,
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

  suspend fun updateAlert(alertEvent: AlertEvent) {
    val dpsAlertId = alertEvent.additionalInformation.alertUuid
    val offenderNo = requireNotNull(alertEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsAlertId" to dpsAlertId,
      "offenderNo" to offenderNo,
      "alertCode" to alertEvent.additionalInformation.alertCode,
    )

    if (alertEvent.wasUpdateInDPS()) {
      runCatching {
        val mapping = mappingApiService.getOrNullByDpsId(dpsAlertId)
          ?: throw IllegalStateException("Tried to update an alert that has never been created")
        telemetryMap["nomisBookingId"] = mapping.nomisBookingId.toString()
        telemetryMap["nomisAlertSequence"] = mapping.nomisAlertSequence.toString()

        val dpsAlert = dpsApiService.getAlert(dpsAlertId)
        nomisApiService.updateAlert(
          bookingId = mapping.nomisBookingId,
          alertSequence = mapping.nomisAlertSequence,
          dpsAlert.toNomisUpdateRequest(),
        )
        telemetryClient.trackEvent("alert-updated-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("alert-updated-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("alert-updated-ignored", telemetryMap)
    }
  }

  suspend fun deleteAlert(alertEvent: AlertEvent) {
    val dpsAlertId = alertEvent.additionalInformation.alertUuid
    val offenderNo = requireNotNull(alertEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsAlertId" to dpsAlertId,
      "offenderNo" to offenderNo,
      "alertCode" to alertEvent.additionalInformation.alertCode,
    )
    if (alertEvent.wasDeletedInDPS()) {
      runCatching {
        mappingApiService.getOrNullByDpsId(dpsAlertId)?.also { mapping ->
          telemetryMap["nomisBookingId"] = mapping.nomisBookingId.toString()
          telemetryMap["nomisAlertSequence"] = mapping.nomisAlertSequence.toString()

          nomisApiService.deleteAlert(bookingId = mapping.nomisBookingId, alertSequence = mapping.nomisAlertSequence)
          tryToDeletedMapping(dpsAlertId)
          telemetryClient.trackEvent("alert-deleted-success", telemetryMap)
        } ?: also {
          telemetryClient.trackEvent("alert-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("alert-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("alert-deleted-ignored", telemetryMap)
    }
  }

  private suspend fun tryToDeletedMapping(dpsAlertId: String) = kotlin.runCatching {
    mappingApiService.deleteByDpsId(dpsAlertId)
  }.onFailure { e ->
    telemetryClient.trackEvent("alert-mapping-deleted-failed", mapOf("dpsAlertId" to dpsAlertId))
    log.warn("Unable to delete mapping for alert $dpsAlertId. Please delete manually", e)
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class AlertEvent(
  val description: String?,
  val eventType: String,
  val additionalInformation: AlertAdditionalInformation,
  val personReference: PersonReference,
)

data class AlertAdditionalInformation(
  val alertUuid: String,
  val alertCode: String,
  val source: AlertSource,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value
  fun findNomsNumber() = get(NOMS_NUMBER_TYPE)

  companion object {
    const val NOMS_NUMBER_TYPE = "NOMS"
    fun withNomsNumber(prisonNumber: String) = PersonReference(listOf(Identifier(NOMS_NUMBER_TYPE, prisonNumber)))
  }

  data class Identifier(val type: String, val value: String)
}

enum class AlertSource {
  DPS,
  NOMIS,
}

fun AlertEvent.wasCreatedInDPS() = wasSourceDPS()
fun AlertEvent.wasUpdateInDPS() = wasSourceDPS()
fun AlertEvent.wasDeletedInDPS() = wasSourceDPS()
fun AlertEvent.wasSourceDPS() = this.additionalInformation.source == AlertSource.DPS
