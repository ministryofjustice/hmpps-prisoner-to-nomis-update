package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.OFFICIAL_VISIT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.MappingRetry.MappingTypes.OFFICIAL_VISITOR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SearchLevelType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.track
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.tryFetchParent
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
          mappingApiService.getVisitByDpsIdOrNull(event.additionalInformation.officialVisitId)
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
  suspend fun visitUpdated(event: VisitEvent) {
    val telemetry = event.asTelemetry()
    if (event.didOriginateInDPS()) {
      track("${OFFICIAL_VISIT.entityName}-update", telemetry) {
        val visitMapping = mappingApiService.getVisitByDpsId(
          dpsVisitId = event.additionalInformation.officialVisitId,
        ).also {
          telemetry["nomisVisitId"] = it.nomisId.toString()
        }
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

        nomisApiService.updateOfficialVisit(
          visitId = visitMapping.nomisId,
          request = dpsVisit.toUpdateOfficialVisitRequest(
            visitSlotId = timeSlotMapping.nomisId,
            internalLocationId = locationMapping.nomisLocationId,
          ),
        )
      }
    } else {
      telemetryClient.trackEvent("${OFFICIAL_VISIT.entityName}-update-ignored", telemetry)
    }
  }

  suspend fun visitDeleted(event: VisitEvent) = telemetryClient.trackEvent("${OFFICIAL_VISIT.entityName}-delete-success", event.asTelemetry())
  suspend fun visitorCreated(event: VisitorEvent) {
    val telemetry = event.asTelemetry()
    if (event.didOriginateInDPS()) {
      synchronise {
        name = OFFICIAL_VISITOR.entityName
        telemetryClient = this@OfficialVisitsService.telemetryClient
        retryQueueService = officialVisitsRetryQueueService
        eventTelemetry = telemetry

        checkMappingDoesNotExist {
          mappingApiService.getVisitorByDpsIdOrNull(event.additionalInformation.officialVisitorId)
        }
        transform {
          val dpsVisitor = dpsApiService.getOfficialVisit(event.additionalInformation.officialVisitId).visitors.find { it.officialVisitorId == event.additionalInformation.officialVisitorId } ?: throw IllegalStateException("Visitor ${event.additionalInformation.officialVisitorId} does not exist on DPS visit")
          val visitMapping = tryFetchParent { mappingApiService.getVisitByDpsIdOrNull(event.additionalInformation.officialVisitId) }.also {
            telemetry["nomisVisitId"] = it.nomisId.toString()
          }
          nomisApiService.createOfficialVisitor(
            visitId = visitMapping.nomisId,
            request = dpsVisitor.toCreateOfficialVisitorRequest(),
          ).also {
            telemetry["nomisVisitorId"] = it.id.toString()
          }.let {
            OfficialVisitorMappingDto(
              dpsId = event.additionalInformation.officialVisitorId.toString(),
              nomisId = it.id,
              mappingType = OfficialVisitorMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createVisitorMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-create-ignored", telemetry)
    }
  }
  suspend fun visitorUpdated(event: VisitorEvent) {
    val telemetry = event.asTelemetry()
    if (event.didOriginateInDPS()) {
      track("${OFFICIAL_VISITOR.entityName}-update", telemetry) {
        val visitMapping = mappingApiService.getVisitByDpsId(
          dpsVisitId = event.additionalInformation.officialVisitId,
        ).also {
          telemetry["nomisVisitId"] = it.nomisId.toString()
        }
        val visitorMapping = mappingApiService.getVisitorByDpsId(
          dpsVisitorId = event.additionalInformation.officialVisitorId,
        ).also {
          telemetry["nomisVisitorId"] = it.nomisId.toString()
        }

        val dpsVisitor = dpsApiService.getOfficialVisit(event.additionalInformation.officialVisitId).visitors.find { it.officialVisitorId == event.additionalInformation.officialVisitorId } ?: throw IllegalStateException("Visitor ${event.additionalInformation.officialVisitorId} does not exist on DPS visit")

        nomisApiService.updateOfficialVisitor(
          visitId = visitMapping.nomisId,
          visitorId = visitorMapping.nomisId,
          request = dpsVisitor.toUpdateOfficialVisitorRequest(),
        )
      }
    } else {
      telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-update-ignored", telemetry)
    }
  }
  suspend fun visitorDeleted(event: VisitorEvent) {
    val telemetry = event.asTelemetry()
    val visitMapping = mappingApiService.getVisitByDpsIdOrNull(
      dpsVisitId = event.additionalInformation.officialVisitId,
    )?.also {
      telemetry["nomisVisitId"] = it.nomisId.toString()
    }
    val visitorMapping = mappingApiService.getVisitorByDpsIdOrNull(
      dpsVisitorId = event.additionalInformation.officialVisitorId,
    )?.also {
      telemetry["nomisVisitorId"] = it.nomisId.toString()
    }

    if (visitMapping != null && visitorMapping != null) {
      track("${OFFICIAL_VISITOR.entityName}-delete", telemetry) {
        nomisApiService.deleteOfficialVisitor(
          visitId = visitMapping.nomisId,
          visitorId = visitorMapping.nomisId,
        )
        mappingApiService.deleteByVisitorNomisId(visitorMapping.nomisId)
      }
    } else {
      telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-delete-ignored", telemetry)
    }
  }

  suspend fun createVisitMapping(message: CreateMappingRetryMessage<OfficialVisitMappingDto>) {
    mappingApiService.createVisitMapping(message.mapping)
    telemetryClient.trackEvent("${OFFICIAL_VISIT.entityName}-create-success", message.telemetryAttributes)
  }
  suspend fun createVisitorMapping(message: CreateMappingRetryMessage<OfficialVisitorMappingDto>) {
    mappingApiService.createVisitorMapping(message.mapping)
    telemetryClient.trackEvent("${OFFICIAL_VISITOR.entityName}-create-success", message.telemetryAttributes)
  }
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
private fun SyncOfficialVisit.toUpdateOfficialVisitRequest(visitSlotId: Long, internalLocationId: Long) = UpdateOfficialVisitRequest(
  visitSlotId = visitSlotId,
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
private fun SyncOfficialVisitor.toCreateOfficialVisitorRequest() = CreateOfficialVisitorRequest(
  personId = this.contactId!!,
  leadVisitor = this.leadVisitor,
  assistedVisit = this.assistedVisit,
  visitorAttendanceOutcomeCode = this.attendanceCode?.toNomisAttendanceCode(),
  commentText = this.visitorNotes,
)

private fun SyncOfficialVisitor.toUpdateOfficialVisitorRequest() = UpdateOfficialVisitorRequest(
  leadVisitor = this.leadVisitor,
  assistedVisit = this.assistedVisit,
  visitorAttendanceOutcomeCode = this.attendanceCode?.toNomisAttendanceCode(),
  commentText = this.visitorNotes,
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
