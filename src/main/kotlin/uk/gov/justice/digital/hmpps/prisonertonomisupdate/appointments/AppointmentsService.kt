package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAppointmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime

@Service
class AppointmentsService(
  private val appointmentsApiService: AppointmentsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: AppointmentMappingService,
  private val appointmentsUpdateQueueService: AppointmentsUpdateQueueService,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun createAppointment(event: AppointmentDomainEvent) {
    appointmentsApiService.getAppointment(event.additionalInformation.id).run {
      val telemetryMap = mutableMapOf(
        "appointmentInstanceId" to id.toString(),
        "bookingId" to bookingId.toString(),
        "locationId" to locationId.toString(),
        "date" to date.toString(),
        "start" to start.toString(),
      )

      // to protect against repeated create messages for same appointment
      if (mappingService.getMappingGivenAppointmentInstanceIdOrNull(id) != null) {
        telemetryClient.trackEvent(
          "appointment-create-duplicate",
          mapOf(
            "appointmentInstanceId" to id.toString(),
            "bookingId" to bookingId.toString(),
            "locationId" to locationId.toString(),
            "date" to date.toString(),
            "start" to start.toString(),
          ),
        )
        return
      }

      val nomisResponse = try {
        nomisApiService.createAppointment(toNomisAppointment(this))
      } catch (e: Exception) {
        telemetryClient.trackEvent("appointment-create-failed", telemetryMap)
        log.error("createAppointment() Unexpected exception", e)
        throw e
      }

      telemetryMap["eventId"] = nomisResponse.eventId.toString()

      try {
        mappingService.createMapping(
          AppointmentMappingDto(nomisEventId = nomisResponse.eventId, appointmentInstanceId = id),
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("appointment-create-map-failed", telemetryMap)
        log.error("Unexpected exception, queueing retry", e)
        appointmentsUpdateQueueService.sendMessage(
          AppointmentContext(nomisEventId = nomisResponse.eventId, appointmentInstanceId = id),
        )
        return
      }

      telemetryClient.trackEvent("appointment-create-success", telemetryMap)
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
    )
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
  val id: Long,
)
