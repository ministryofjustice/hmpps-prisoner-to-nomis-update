package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.OFFICIAL_VISIT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.OFFICIAL_VISITOR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage

@Service
class OfficialVisitsService(private val telemetryClient: TelemetryClient) {
  suspend fun visitCreated(event: VisitEvent) = telemetryClient.trackEvent("${OFFICIAL_VISIT.entityName}-create-success", event.asTelemetry())
  suspend fun visitorCreated(event: VisitorEvent) = telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-create-success", event.asTelemetry())
  suspend fun createVisitMapping(message: CreateMappingRetryMessage<OfficialVisitMappingDto>) = telemetryClient.trackEvent("${OFFICIAL_VISIT.entityName}-create-success", message.telemetryAttributes)
  suspend fun createVisitorMapping(message: CreateMappingRetryMessage<OfficialVisitorMappingDto>) = telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-create-success", message.telemetryAttributes)
}

fun VisitEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to additionalInformation.prisonId,
  "dpsOfficialVisitId" to additionalInformation.officialVisitId,
  "offenderNo" to prisonerNumber(),
  "source" to additionalInformation.source,
)

fun VisitorEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to additionalInformation.prisonId,
  "dpsOfficialVisitId" to additionalInformation.officialVisitId,
  "dpsOfficialVisitorId" to additionalInformation.officialVisitorId,
  "dpsContactId" to contactId(),
  "source" to additionalInformation.source,
)
