package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAppointmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateAppointmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime
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
    val telemetryMap =
      mutableMapOf("appointmentInstanceId" to event.additionalInformation.appointmentInstanceId.toString())

    runCatching {
      val appointmentInstance = appointmentsApiService.getAppointmentInstance(event.additionalInformation.appointmentInstanceId)

      val nomisEventId =
        mappingService.getMappingGivenAppointmentInstanceId(event.additionalInformation.appointmentInstanceId).nomisEventId
          .also { telemetryMap["nomisEventId"] = it.toString() }

      nomisApiService.updateAppointment(nomisEventId, toUpdateAppointmentRequest(appointmentInstance))
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
      val nomisEventId =
        mappingService.getMappingGivenAppointmentInstanceId(event.additionalInformation.appointmentInstanceId).nomisEventId
          .also { telemetryMap["nomisEventId"] = it.toString() }

      nomisApiService.cancelAppointment(nomisEventId)
    }.onSuccess {
      telemetryClient.trackEvent("appointment-cancel-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("appointment-cancel-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun uncancelAppointment(event: AppointmentDomainEvent) {
    val telemetryMap =
      mutableMapOf("appointmentInstanceId" to event.additionalInformation.appointmentInstanceId.toString())

    runCatching {
      val nomisEventId =
        mappingService.getMappingGivenAppointmentInstanceId(event.additionalInformation.appointmentInstanceId).nomisEventId
          .also { telemetryMap["nomisEventId"] = it.toString() }

      nomisApiService.uncancelAppointment(nomisEventId)
    }.onSuccess {
      telemetryClient.trackEvent("appointment-uncancel-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("appointment-uncancel-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun deleteAppointment(event: AppointmentDomainEvent) {
    val telemetryMap =
      mutableMapOf("appointmentInstanceId" to event.additionalInformation.appointmentInstanceId.toString())

    runCatching {
      val nomisEventId =
        mappingService.getMappingGivenAppointmentInstanceId(event.additionalInformation.appointmentInstanceId).nomisEventId
          .also { telemetryMap["nomisEventId"] = it.toString() }

      nomisApiService.deleteAppointment(nomisEventId)
    }.onSuccess {
      telemetryClient.trackEvent("appointment-delete-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("appointment-delete-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun deleteAllAppointments() {
    mappingService.getAllMappings().forEach { mapping ->
      runCatching {
        nomisApiService.deleteAppointment(mapping.nomisEventId)
        mappingService.deleteMapping(mapping.appointmentInstanceId)
      }.onSuccess {
        telemetryClient.trackEvent(
          "appointment-DELETE-ALL-success",
          mapOf(
            "nomisEventId" to mapping.nomisEventId.toString(),
            "appointmentInstanceId" to mapping.appointmentInstanceId.toString(),
          ),
          null,
        )
      }.onFailure { e ->
        log.error("Failed to delete appointment with appointmentInstanceId ${mapping.appointmentInstanceId}", e)
        telemetryClient.trackEvent(
          "appointment-DELETE-ALL-failed",
          mapOf(
            "nomisEventId" to mapping.nomisEventId.toString(),
            "appointmentInstanceId" to mapping.appointmentInstanceId.toString(),
          ),
          null,
        )
      }
    }
  }

  private fun toCreateAppointmentRequest(instance: AppointmentInstance) = CreateAppointmentRequest(
    bookingId = instance.bookingId,
    internalLocationId = if (instance.inCell) { null } else { instance.internalLocationId },
    eventDate = instance.appointmentDate,
    startTime = LocalTime.parse(instance.startTime),
    endTime = instance.endTime?.let { LocalTime.parse(it) },
    eventSubType = instance.categoryCode,
    comment = constructComment(instance),
  )

  private fun toUpdateAppointmentRequest(instance: AppointmentInstance) = UpdateAppointmentRequest(
    internalLocationId = if (instance.inCell) { null } else { instance.internalLocationId },
    eventDate = instance.appointmentDate,
    startTime = LocalTime.parse(instance.startTime),
    endTime = instance.endTime?.let { LocalTime.parse(it) },
    eventSubType = instance.categoryCode,
    comment = constructComment(instance),
  )

  private fun constructComment(instance: AppointmentInstance): String? {
    val comment = if (instance.customName.isNullOrBlank()) {
      if (instance.extraInformation.isNullOrBlank()) {
        null
      } else {
        instance.extraInformation
      }
    } else {
      if (instance.extraInformation.isNullOrBlank()) {
        instance.customName
      } else {
        "${instance.customName} - ${instance.extraInformation}"
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
  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class AppointmentDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
  val additionalInformation: AppointmentAdditionalInformation,
)

data class AppointmentAdditionalInformation(
  val appointmentInstanceId: Long,
)
