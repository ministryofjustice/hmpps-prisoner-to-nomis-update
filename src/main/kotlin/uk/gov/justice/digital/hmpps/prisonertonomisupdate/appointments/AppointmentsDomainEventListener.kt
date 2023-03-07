package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import java.util.concurrent.CompletableFuture

@Service
class AppointmentsDomainEventListener(
  private val appointmentsService: AppointmentsService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("appointment", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-hmpps_prisoner_to_nomis_appointment_queue", kind = SpanKind.SERVER)
  fun onChange(
    message: String,
  ): CompletableFuture<Void> {
    log.debug("Received appointment message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val (eventType) = objectMapper.readValue<HMPPSDomainEvent>(sqsMessage.Message)
          if (eventFeatureSwitch.isEnabled(eventType)) {
            when (eventType) {
              "appointments.appointment.created" -> appointmentsService.createAppointment(
                objectMapper.readValue(
                  sqsMessage.Message,
                ),
              )

              else -> log.info("Received a message I wasn't expecting: {}", eventType)
            }
          } else {
            log.warn("Feature switch is disabled for {}", eventType)
          }
        }

        "RETRY" -> {
          val context = objectMapper.readValue<AppointmentContext>(sqsMessage.Message)
          telemetryClient.trackEvent(
            "appointment-retry-received",
            mapOf("appointmentInstanceId" to context.appointmentInstanceId.toString()),
          )

          appointmentsService.createRetry(context)

          telemetryClient.trackEvent(
            "appointment-retry-success",
            mapOf("appointmentInstanceId" to context.appointmentInstanceId.toString()),
          )
        }
      }
    }
  }
}

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}
