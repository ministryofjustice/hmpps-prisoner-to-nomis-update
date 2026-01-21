package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class AppointmentsDomainEventListener(
  private val appointmentsService: AppointmentsService,
  jsonMapper: JsonMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = appointmentsService,
  jsonMapper = jsonMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
  domain = "appointments",
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("appointment", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(rawMessage: String): CompletableFuture<Void?> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "appointments.appointment-instance.created" -> appointmentsService.createAppointment(message.fromJson())
      "appointments.appointment-instance.updated" -> appointmentsService.updateAppointment(message.fromJson())
      "appointments.appointment-instance.cancelled" -> appointmentsService.cancelAppointment(message.fromJson())
      "appointments.appointment-instance.uncancelled" -> appointmentsService.uncancelAppointment(message.fromJson())
      "appointments.appointment-instance.deleted" -> appointmentsService.deleteAppointment(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
