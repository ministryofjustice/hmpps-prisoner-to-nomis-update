package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
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
class ActivitiesDomainEventListener(
  private val activitiesService: ActivitiesService,
  private val allocationService: AllocationService,
  private val attendanceService: AttendanceService,
  private val schedulesService: SchedulesService,
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
  telemetryClient: TelemetryClient,
) : DomainEventListener(
  service = activitiesService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
  telemetryClient = telemetryClient,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("activity", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-hmpps_prisoner_to_nomis_activity_queue", kind = SpanKind.SERVER)
  fun onMessage(rawMessage: String): CompletableFuture<Void> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "activities.activity-schedule.created" -> activitiesService.createActivityEvent(message.fromJson())
      "activities.activity-schedule.amended" -> activitiesService.updateActivityEvent(message.fromJson())
      "activities.scheduled-instance.amended" -> schedulesService.updateScheduledInstance(message.fromJson())
      "activities.prisoner.allocated" -> allocationService.upsertAllocationEvent(message.fromJson())
      "activities.prisoner.allocation-amended" -> allocationService.upsertAllocationEvent(message.fromJson())
      "activities.prisoner.attendance-created" -> attendanceService.upsertAttendanceEvent(message.fromJson())
      "activities.prisoner.attendance-amended" -> attendanceService.upsertAttendanceEvent(message.fromJson())
      "activities.prisoner.attendance-expired" -> attendanceService.upsertAttendanceEvent(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
