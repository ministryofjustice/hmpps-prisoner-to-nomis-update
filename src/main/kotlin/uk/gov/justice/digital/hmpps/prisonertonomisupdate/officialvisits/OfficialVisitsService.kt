package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.OFFICIAL_VISIT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.OFFICIAL_VISITOR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SearchLevelType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalTime

@Service
class OfficialVisitsService(
  override val telemetryClient: TelemetryClient,
  private val mappingApiService: OfficialVisitsMappingService,
  private val visitSlotsMappingApiService: VisitSlotsMappingService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val officialVisitsRetryQueueService: OfficialVisitsRetryQueueService,
) : TelemetryEnabled {
  suspend fun visitCreated(event: VisitEvent) {
    val telemetry = event.asTelemetry()
    if (event.didOriginateInDPS()) {
      synchronise {
        name = OFFICIAL_VISIT.entityName
        telemetryClient = this@OfficialVisitsService.telemetryClient
        retryQueueService = officialVisitsRetryQueueService
        eventTelemetry = telemetry

        checkMappingDoesNotExist {
          mappingApiService.getVisitByDpsIdsOrNull(event.additionalInformation.officialVisitId)
        }
        transform {
          val dpsVisit = dpsApiService.getOfficialVisit(event.additionalInformation.officialVisitId).also {
            telemetry["dpsVisitSlotId"] = it.prisonVisitSlotId.toString()
            telemetry["dpsLocationId"] = it.dpsLocationId.toString()
          }
          val timeSlotMapping = visitSlotsMappingApiService.getVisitSlotByDpsId(dpsVisit.prisonVisitSlotId.toString()).also {
            telemetry["nomisVisitSlotId"] = it.nomisId.toString()
          }
          val locationMapping = visitSlotsMappingApiService.getInternalLocationByDpsId(dpsVisit.dpsLocationId.toString()).also {
            telemetry["nomisLocationId"] = it.nomisLocationId.toString()
          }
          nomisApiService.createOfficialVisit(
            offenderNo = dpsVisit.prisonerNumber,
            request = dpsVisit.toCreateOfficialVisitRequest(
              visitSlotId = timeSlotMapping.nomisId,
              internalLocationId = locationMapping.nomisLocationId,
            ),
          ).also {
            telemetry["nomisVisitId"] = it.visitId.toString()
          }.let {
            OfficialVisitMappingDto(
              dpsId = event.additionalInformation.officialVisitId.toString(),
              nomisId = it.visitId,
              mappingType = OfficialVisitMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createVisitMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${OFFICIAL_VISIT.entityName}-create-ignored", telemetry)
    }
  }
  suspend fun visitDeleted(event: VisitEvent) = telemetryClient.trackEvent("${OFFICIAL_VISIT.entityName}-delete-success", event.asTelemetry())
  suspend fun visitorCreated(event: VisitorEvent) = telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-create-success", event.asTelemetry())
  suspend fun visitorUpdated(event: VisitorEvent) = telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-update-success", event.asTelemetry())
  suspend fun visitorDeleted(event: VisitorDeletedEvent) = telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-delete-success", event.asTelemetry())
  suspend fun createVisitMapping(message: CreateMappingRetryMessage<OfficialVisitMappingDto>) {
    mappingApiService.createVisitMapping(message.mapping)
    telemetryClient.trackEvent("${OFFICIAL_VISIT.entityName}-create-success", message.telemetryAttributes)
  }
  suspend fun createVisitorMapping(message: CreateMappingRetryMessage<OfficialVisitorMappingDto>) = telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-create-success", message.telemetryAttributes)
}

fun VisitEvent.asTelemetry() = mutableMapOf(
  "prisonId" to additionalInformation.prisonId,
  "dpsOfficialVisitId" to additionalInformation.officialVisitId.toString(),
  "offenderNo" to prisonerNumber(),
  "source" to additionalInformation.source,
)

fun VisitorEvent.asTelemetry() = mutableMapOf(
  "prisonId" to additionalInformation.prisonId,
  "dpsOfficialVisitId" to additionalInformation.officialVisitId.toString(),
  "dpsOfficialVisitorId" to additionalInformation.officialVisitorId.toString(),
  "dpsContactId" to contactId().toString(),
  "source" to additionalInformation.source,
)

fun VisitorDeletedEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to additionalInformation.prisonId,
  "dpsOfficialVisitId" to additionalInformation.officialVisitId,
  "dpsOfficialVisitorId" to additionalInformation.officialVisitorId,
  "source" to additionalInformation.source,
)

private fun SourcedEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"

private fun SyncOfficialVisit.toCreateOfficialVisitRequest(visitSlotId: Long, internalLocationId: Long) = CreateOfficialVisitRequest(
  visitSlotId = visitSlotId,
  prisonId = prisonCode,
  startDateTime = visitDate.atTime(LocalTime.parse(startTime)),
  endDateTime = visitDate.atTime(LocalTime.parse(endTime)),
  internalLocationId = internalLocationId,
  visitStatusCode = statusCode.toNomisVisitStatusCode(),
  visitOutcomeCode = completionCode?.toNomisVisitOutcomeCode(),
  prisonerAttendanceCode = prisonerAttendance?.toNomisAttendanceCode(),
  prisonerSearchTypeCode = searchType?.toNomisSearchTypeCode(),
  visitorConcernText = visitorConcernNotes,
  commentText = visitComments,
  overrideBanStaffUsername = overrideBanStaffUsername,
)

private fun VisitStatusType.toNomisVisitStatusCode() = when (this) {
  VisitStatusType.COMPLETED -> "COMP"
  VisitStatusType.CANCELLED -> "CANC"
  VisitStatusType.SCHEDULED -> "SCH"
  VisitStatusType.EXPIRED -> "EXP"
}

// TODO - how correct is this for creating a visit
private fun VisitCompletionType.toNomisVisitOutcomeCode() = when (this) {
  VisitCompletionType.NORMAL -> null
  VisitCompletionType.PRISONER_EARLY -> null
  VisitCompletionType.PRISONER_REFUSED -> "REFUSED"
  VisitCompletionType.STAFF_EARLY -> null
  VisitCompletionType.VISITOR_DENIED -> "NO_ID"
  VisitCompletionType.VISITOR_EARLY -> null
  VisitCompletionType.VISITOR_NO_SHOW -> "NSHOW"
  VisitCompletionType.PRISONER_CANCELLED -> "OFFCANC"
  VisitCompletionType.STAFF_CANCELLED -> "HMP"
  VisitCompletionType.VISITOR_CANCELLED -> "VISCANC"
}

private fun AttendanceType.toNomisAttendanceCode() = when (this) {
  AttendanceType.ATTENDED -> "ATT"
  AttendanceType.ABSENT -> "ABS"
}

private fun SearchLevelType.toNomisSearchTypeCode() = when (this) {
  SearchLevelType.FULL -> "FULL"
  SearchLevelType.PAT -> "PAT"
  SearchLevelType.RUB -> "RUB"
  SearchLevelType.RUB_A -> "RUB_A"
  SearchLevelType.RUB_B -> "RUB_B"
  SearchLevelType.STR -> "STR"
}
