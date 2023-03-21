package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAppointmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime

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
      eventTelemetry = mapOf("appointmentInstanceId" to event.additionalInformation.id.toString())

      checkMappingDoesNotExist {
        mappingService.getMappingGivenAppointmentInstanceIdOrNull(event.additionalInformation.id)
      }
      transform {
        appointmentsApiService.getAppointment(event.additionalInformation.id).run {
          eventTelemetry += "bookingId" to bookingId.toString()
          eventTelemetry += "locationId" to locationId.toString()
          eventTelemetry += "date" to date.toString()
          eventTelemetry += "start" to start.toString()

          nomisApiService.createAppointment(toNomisAppointment(this)).let { nomisResponse ->
            AppointmentMappingDto(nomisEventId = nomisResponse.eventId, appointmentInstanceId = id)
          }
        }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  private fun toNomisAppointment(appointment: Appointment): CreateAppointmentRequest =
    CreateAppointmentRequest(
      bookingId = appointment.bookingId,
      internalLocationId = appointment.locationId,
      eventDate = appointment.date,
      startTime = appointment.start,
      endTime = appointment.end,
      eventSubType = appointment.eventSubType,
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
  val id: Long,
)
