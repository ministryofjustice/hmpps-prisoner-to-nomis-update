package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

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
class ActivitiesDomainEventListener(
  private val activitiesService: ActivitiesService,
  private val allocationService: AllocationService,
  private val attendanceService: AttendanceService,
  private val schedulesService: SchedulesService,
  objectMapper: ObjectMapper,
  eventFeatureSwitch: EventFeatureSwitch,
) : DomainEventListener(
  service = activitiesService,
  objectMapper = objectMapper,
  eventFeatureSwitch = eventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("activity", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-hmpps_prisoner_to_nomis_activity_queue", kind = SpanKind.SERVER)
  fun onMessage(rawMessage: String): CompletableFuture<Void> = onDomainEvent(rawMessage) { eventType, message ->
    when (eventType) {
      "activities.activity-schedule.created" -> activitiesService.createActivity(message.fromJson())
      "activities.activity-schedule.amended" -> activitiesService.updateActivity(message.fromJson())
      "activities.scheduled-instance.amended" -> schedulesService.updateScheduledInstance(message.fromJson())
      "activities.prisoner.allocated" -> allocationService.upsertAllocation(message.fromJson())
      "activities.prisoner.allocation-amended" -> allocationService.upsertAllocation(message.fromJson())
      "activities.prisoner.attendance-created" -> attendanceService.upsertAttendance(message.fromJson())
      "activities.prisoner.attendance-amended" -> attendanceService.upsertAttendance(message.fromJson())
      "activities.prisoner.attendance-expired" -> attendanceService.upsertAttendance(message.fromJson())

      else -> log.info("Received a message I wasn't expecting: {}", eventType)
    }
  }
}
