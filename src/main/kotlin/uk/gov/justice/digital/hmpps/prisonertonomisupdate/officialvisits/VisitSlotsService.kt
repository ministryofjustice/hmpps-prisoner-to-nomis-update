package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.TIME_SLOT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.VISIT_SLOT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage

@Service
class VisitSlotsService(private val telemetryClient: TelemetryClient) {

  suspend fun timeSlotCreated(event: TimeSlotEvent) = telemetryClient.trackEvent("${TIME_SLOT.entityName}-create-success", event.asTelemetry())
  suspend fun timeSlotUpdated(event: TimeSlotEvent) = telemetryClient.trackEvent("${TIME_SLOT.entityName}-update-success", event.asTelemetry())
  suspend fun timeSlotDeleted(event: TimeSlotEvent) = telemetryClient.trackEvent("${TIME_SLOT.entityName}-delete-success", event.asTelemetry())
  suspend fun visitSlotCreated(event: VisitSlotEvent) = telemetryClient.trackEvent("${VISIT_SLOT.entityName}-create-success", event.asTelemetry())
  suspend fun visitSlotUpdated(event: VisitSlotEvent) = telemetryClient.trackEvent("${VISIT_SLOT.entityName}-update-success", event.asTelemetry())
  suspend fun visitSlotDeleted(event: VisitSlotEvent) = telemetryClient.trackEvent("${VISIT_SLOT.entityName}-delete-success", event.asTelemetry())
  suspend fun createTimeSlotMapping(message: CreateMappingRetryMessage<VisitTimeSlotMappingDto>) = telemetryClient.trackEvent("${TIME_SLOT.entityName}-create-success", message.telemetryAttributes)
  suspend fun createVisitSlotMapping(message: CreateMappingRetryMessage<VisitSlotMappingDto>) = telemetryClient.trackEvent("${VISIT_SLOT.entityName}-create-success", message.telemetryAttributes)
}

fun TimeSlotEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to additionalInformation.prisonId,
  "dpsTimeSlotId" to additionalInformation.timeSlotId,
  "source" to additionalInformation.source,
)

fun VisitSlotEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to additionalInformation.prisonId,
  "dpsVisitSlotId" to additionalInformation.visitSlotId,
  "source" to additionalInformation.source,
)
