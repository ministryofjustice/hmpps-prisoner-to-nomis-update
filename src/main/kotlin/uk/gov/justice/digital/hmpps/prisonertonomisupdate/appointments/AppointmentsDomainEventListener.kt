package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DomainEventListener
import java.util.concurrent.CompletableFuture

@Service
class AppointmentsDomainEventListener(
  private val appointmentsService: AppointmentsService,
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
) : DomainEventListener(
  service = appointmentsService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("appointment", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-hmpps_prisoner_to_nomis_appointment_queue", kind = SpanKind.SERVER)
  fun onMessage(rawMessage: String): CompletableFuture<Void> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "appointments.appointment-instance.created" -> appointmentsService.createAppointment(message.fromJson())
      "appointments.appointment-instance.updated" -> appointmentsService.updateAppointment(message.fromJson())
      "appointments.appointment-instance.cancelled" -> appointmentsService.cancelAppointment(message.fromJson())
      "appointments.appointment-instance.deleted" -> appointmentsService.deleteAppointment(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
