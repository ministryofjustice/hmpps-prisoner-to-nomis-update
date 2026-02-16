package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AppointmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAppointmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateAppointmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalTime

@Service
class AppointmentsService(
  private val appointmentsApiService: AppointmentsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: AppointmentMappingService,
  private val appointmentsUpdateQueueService: AppointmentsUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createAppointment(event: AppointmentDomainEvent) {
    synchronise {
      name = "appointment"
      telemetryClient = this@AppointmentsService.telemetryClient
      retryQueueService = appointmentsUpdateQueueService
      eventTelemetry = mapOf("appointmentInstanceId" to event.additionalInformation.appointmentInstanceId.toString())

      checkMappingDoesNotExist {
        mappingService.getMappingGivenAppointmentInstanceIdOrNull(event.additionalInformation.appointmentInstanceId)
      }
      transform {
        appointmentsApiService.getAppointmentInstance(event.additionalInformation.appointmentInstanceId).run {
          val request = toCreateAppointmentRequest(this)

          eventTelemetry += "bookingId" to request.bookingId.toString()
          eventTelemetry += "locationId" to request.internalLocationId.toString()
          eventTelemetry += "date" to request.eventDate.toString()
          eventTelemetry += "start" to request.startTime.toString()

          AppointmentMappingDto(
            nomisEventId = nomisApiService.createAppointment(request).eventId,
            appointmentInstanceId = id,
          )
        }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  suspend fun updateAppointment(event: AppointmentDomainEvent) {
    val (appointmentInstanceId) = event.additionalInformation
    val telemetryMap =
      mutableMapOf("appointmentInstanceId" to appointmentInstanceId.toString())

    runCatching {
      val appointmentInstance = try {
        appointmentsApiService.getAppointmentInstance(appointmentInstanceId)
      } catch (ed: WebClientResponseException.NotFound) {
        try {
          mappingService.getMappingGivenAppointmentInstanceId(appointmentInstanceId).nomisEventId
          // If the appointment doesn't exist in DPS but does in the mapping service, then we should keep retrying
          throw RuntimeException(ed)
        } catch (em: WebClientResponseException.NotFound) {
          telemetryMap["dps-error"] = ed.message ?: ed.javaClass.name
          telemetryMap["mapping-error"] = em.message ?: em.javaClass.name
          telemetryClient.trackEvent("appointment-amend-missing-ignored", telemetryMap, null)
          return
        }
      }
      mappingService.getMappingGivenAppointmentInstanceId(appointmentInstanceId).nomisEventId
        .also {
          telemetryMap["nomisEventId"] = it.toString()
        }
        .also {
          nomisApiService.updateAppointment(it, toUpdateAppointmentRequest(appointmentInstance))
        }
    }.onSuccess {
      telemetryClient.trackEvent("appointment-amend-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("appointment-amend-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun cancelAppointment(event: AppointmentDomainEvent) {
    val telemetryMap =
      mutableMapOf("appointmentInstanceId" to event.additionalInformation.appointmentInstanceId.toString())

    runCatching {
      val nomisEventId = try {
        mappingService.getMappingGivenAppointmentInstanceId(event.additionalInformation.appointmentInstanceId).nomisEventId
          .also { telemetryMap["nomisEventId"] = it.toString() }
      } catch (em: WebClientResponseException.NotFound) {
        try {
          appointmentsApiService.getAppointmentInstance(event.additionalInformation.appointmentInstanceId)
          // If the appointment exists in DPS but not in the mapping service, then we should keep retrying
          throw RuntimeException(em)
        } catch (ed: WebClientResponseException.NotFound) {
          // Here it means the appointment does not exist in DPS nor in the mapping table, so it was genuinely deleted and we can ignore it
          telemetryMap["dps-error"] = ed.message ?: ed.javaClass.name
          telemetryMap["mapping-error"] = em.message ?: em.javaClass.name
          telemetryClient.trackEvent("appointment-cancel-missing-ignored", telemetryMap, null)
          return
        }
      }
      if (!nomisApiService.cancelAppointmentIgnoreIfNotFound(nomisEventId)) {
        // Ignore event if appointment does not exist in Nomis
        // This can happen if a deletion and cancellation event arrive simultaneously, and the
        // cancellation process gets the mapping before it is deleted by the deletion processing, but then
        // finds that the deletion has deleted it in Nomis already.
        telemetryMap["nomis-error"] = "404 Not found in Nomis: event ignored"
      }
    }.onSuccess {
      telemetryClient.trackEvent("appointment-cancel-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("appointment-cancel-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun uncancelAppointment(event: AppointmentDomainEvent) {
    val (appointmentInstanceId) = event.additionalInformation
    val telemetryMap =
      mutableMapOf("appointmentInstanceId" to appointmentInstanceId.toString())

    runCatching {
      mappingService.getMappingGivenAppointmentInstanceId(appointmentInstanceId).nomisEventId
        .also {
          telemetryMap["nomisEventId"] = it.toString()
        }
        .also {
          nomisApiService.uncancelAppointment(it)
        }
    }.onSuccess {
      telemetryClient.trackEvent("appointment-uncancel-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("appointment-uncancel-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun deleteAppointment(event: AppointmentDomainEvent) {
    val (appointmentInstanceId) = event.additionalInformation
    val telemetryMap =
      mutableMapOf("appointmentInstanceId" to appointmentInstanceId.toString())

    runCatching {
      try {
        mappingService.getMappingGivenAppointmentInstanceId(appointmentInstanceId).nomisEventId
          .also { telemetryMap["nomisEventId"] = it.toString() }
      } catch (e: WebClientResponseException.NotFound) {
        telemetryMap["error"] = e.message ?: e.javaClass.name
        telemetryClient.trackEvent("appointment-delete-missing-mapping-ignored", telemetryMap, null)
        return
      }
        .also {
          try {
            nomisApiService.deleteAppointment(it)
          } catch (e: WebClientResponseException.NotFound) {
            telemetryMap["error"] = e.message ?: e.javaClass.name
            telemetryClient.trackEvent("appointment-delete-missing-nomis-ignored", telemetryMap, null)
          }
        }
      mappingService.deleteMapping(appointmentInstanceId)
    }.onSuccess {
      telemetryClient.trackEvent("appointment-delete-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("appointment-delete-failed", telemetryMap, null)
      throw e
    }
  }

  private suspend fun toCreateAppointmentRequest(instance: AppointmentInstance) = CreateAppointmentRequest(
    bookingId = instance.bookingId,
    internalLocationId = if (instance.inCell) {
      null
    } else {
      instance.dpsLocationId?.let { mappingService.getLocationMappingGivenDpsId(it).nomisLocationId }
    },
    eventDate = instance.appointmentDate,
    startTime = LocalTime.parse(instance.startTime),
    endTime = instance.endTime?.let { LocalTime.parse(it) },
    eventSubType = instance.categoryCode,
    comment = constructComment(instance),
  )

  private suspend fun toUpdateAppointmentRequest(instance: AppointmentInstance) = UpdateAppointmentRequest(
    internalLocationId = if (instance.inCell) {
      null
    } else {
      instance.dpsLocationId?.let { mappingService.getLocationMappingGivenDpsId(it).nomisLocationId }
    },
    eventDate = instance.appointmentDate,
    startTime = LocalTime.parse(instance.startTime),
    endTime = instance.endTime?.let { LocalTime.parse(it) },
    eventSubType = instance.categoryCode,
    comment = constructComment(instance),
  )

  private fun constructComment(instance: AppointmentInstance): String? {
    val comment = if (instance.customName.isNullOrBlank()) {
      if (instance.prisonerExtraInformation.isNullOrBlank()) {
        null
      } else {
        instance.prisonerExtraInformation
      }
    } else {
      if (instance.prisonerExtraInformation.isNullOrBlank()) {
        instance.customName
      } else {
        "${instance.customName} - ${instance.prisonerExtraInformation}"
      }
    }
    return comment?.take(4000)
  }

  suspend fun createRetry(message: CreateMappingRetryMessage<AppointmentMappingDto>) {
    mappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "appointment-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())
  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class AppointmentDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val additionalInformation: AppointmentAdditionalInformation,
)

data class AppointmentAdditionalInformation(
  val appointmentInstanceId: Long,
)
