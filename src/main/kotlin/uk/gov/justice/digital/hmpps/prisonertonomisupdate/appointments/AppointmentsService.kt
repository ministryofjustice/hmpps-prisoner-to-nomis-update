package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Appointment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentOccurrenceDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAppointmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
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
//          val occurrence = appointmentsApiService.getAppointmentOccurrence(appointmentOccurrenceId)
//          val appointment = appointmentsApiService.getAppointment(appointmentId)
          val request = toNomisAppointment(this, null, null) // occurrence, appointment)

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

  private fun toNomisAppointment(
    instance: AppointmentInstance,
    occurrence: AppointmentOccurrenceDetails?,
    appointment: Appointment?,
  ): CreateAppointmentRequest = CreateAppointmentRequest(
    bookingId = instance.bookingId,
    internalLocationId = if (instance.inCell) {
      null
    } else {
      instance.internalLocationId // ?: occurrence.internalLocation?.id ?: appointment.internalLocationId
    },
    eventDate = instance.appointmentDate,
    startTime = LocalTime.parse(instance.startTime),
    endTime = LocalTime.parse(instance.endTime),
    eventSubType = instance.categoryCode,
  )

  suspend fun createRetry(context: AppointmentContext) {
    mappingService.createMapping(
      AppointmentMappingDto(nomisEventId = context.nomisEventId, appointmentInstanceId = context.appointmentInstanceId),
    ).also {
      telemetryClient.trackEvent(
        "appointment-create-mapping-retry-success",
        mapOf("appointmentInstanceId" to context.appointmentInstanceId.toString()),
      )
    }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())
  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
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
