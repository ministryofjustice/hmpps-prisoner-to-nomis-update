package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
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
      eventTelemetry = mapOf("appointmentInstanceId" to event.additionalInformation.id.toString())

      checkMappingDoesNotExist {
        mappingService.getMappingGivenAppointmentInstanceIdOrNull(event.additionalInformation.id)
      }
      transform {
        appointmentsApiService.getAppointmentInstance(event.additionalInformation.id).run {
          // val occurrence = appointmentsApiService.getAppointmentOccurrence(??) // TODO coalesce with parent record values
          eventTelemetry += "bookingId" to bookingId.toString()
          eventTelemetry += "locationId" to internalLocationId.toString()
          eventTelemetry += "date" to appointmentDate.toString()
          eventTelemetry += "start" to startTime

          AppointmentMappingDto(
            nomisEventId = nomisApiService.createAppointment(toNomisAppointment(this)).eventId,
            appointmentInstanceId = id,
          )
        }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  private fun toNomisAppointment(instance: AppointmentInstance): CreateAppointmentRequest =
    CreateAppointmentRequest(
      bookingId = instance.bookingId,
      internalLocationId = instance.internalLocationId!!,
      eventDate = instance.appointmentDate,
      startTime = LocalTime.parse(instance.startTime),
      endTime = LocalTime.parse(instance.endTime),
      eventSubType = instance.category.code,
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
