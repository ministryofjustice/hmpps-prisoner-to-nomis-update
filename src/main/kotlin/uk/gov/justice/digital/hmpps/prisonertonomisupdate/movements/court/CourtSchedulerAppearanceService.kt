package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerRetryService.Companion.MappingTypes.SCHEDULE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import java.util.*

@Service
class CourtSchedulerAppearanceService(
  private val mappingApi: CourtSchedulerMappingApiService,
  private val dpsApi: CourtSchedulerDpsApiService,
  private val nomisApi: CourtSchedulerNomisApiService,
  private val retryQueueService: CourtSchedulerRetryQueueService,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    private val TELEMETRY_KEY = "court-scheduler-schedule"
    private val TELEMETRY_KEY_CREATE = "$TELEMETRY_KEY-create"
    private val TELEMETRY_KEY_UPDATE = "$TELEMETRY_KEY-update"

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun courtAppearanceChanged(event: CourtSchedulerEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsCourtAppearanceId = event.additionalInformation.id
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsCourtAppearanceId" to dpsCourtAppearanceId.toString(),
    )
    var telemetryKey = TELEMETRY_KEY_CREATE

    if (event.additionalInformation.source != "DPS") {
      telemetryClient.trackEvent("$telemetryKey-ignored", telemetryMap)
      return
    }

    runCatching {
      val existingMapping = mappingApi.getCourtScheduleMapping(dpsCourtAppearanceId)
      if (existingMapping != null) telemetryKey = TELEMETRY_KEY_UPDATE
      val dps = dpsApi.getCourtAppearance(dpsCourtAppearanceId)
      val nomis = nomisApi.upsertCourtScheduleOut(prisonerNumber, dps.toNomisUpsertRequest(existingMapping?.nomisEventId))
        .also {
          telemetryMap["bookingId"] = it.bookingId.toString()
          telemetryMap["nomisEventId"] = it.eventId.toString()
        }

      if (existingMapping == null) {
        createCourtAppearanceMapping(prisonerNumber, nomis.bookingId, nomis.eventId, dpsCourtAppearanceId, telemetryMap)
      } else {
        telemetryClient.trackEvent("$telemetryKey-success", telemetryMap, null)
      }
    }
      .onFailure {
        telemetryMap["error"] = it.message ?: "Unknown error"
        telemetryClient.trackEvent("$telemetryKey-error", telemetryMap)
        throw it
      }
  }

  private suspend fun createCourtAppearanceMapping(
    prisonerNumber: String,
    bookingId: Long,
    eventId: Long,
    dpsCourtAppearanceId: UUID,
    telemetryMap: MutableMap<String, String>,
  ) = CourtScheduleMappingDto(prisonerNumber, bookingId, eventId, dpsCourtAppearanceId, DPS_CREATED)
    .also {
      createMapping(
        it,
        telemetryClient,
        { createCourtAppearanceMapping(it, telemetryMap) },
        telemetryMap,
        retryQueueService,
        SCHEDULE.entityName,
        log,
        failureSuffix = "error",
        failureReasonKey = "error",
      )
    }

  suspend fun createCourtAppearanceMapping(message: CreateMappingRetryMessage<CourtScheduleMappingDto>) {
    createCourtAppearanceMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun createCourtAppearanceMapping(mapping: CourtScheduleMappingDto, telemetry: Map<String, String>) {
    mappingApi.createCourtScheduleMapping(mapping).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_KEY_CREATE-success",
        telemetry,
      )
    }
  }
}

private fun CourtEvent.toNomisUpsertRequest(eventId: Long? = null): UpsertCourtScheduleOut {
  val (status, returnStatus) = eventStatus.toNomisSchedulesStatus()
  return UpsertCourtScheduleOut(
    eventId = eventId,
    startTime = start,
    eventType = courtEventType,
    eventStatus = status,
    returnStatus = returnStatus,
    prison = prisonCodeAtTimeOfScheduling,
    court = agyLocId,
    comment = commentText,
  )
}

fun String.toNomisSchedulesStatus() = when (this) {
  "SCHEDULED" -> "SCH" to null
  "EXPIRED" -> "EXP" to null
  "IN_PROGRESS" -> "COMP" to "SCH"
  "COMPLETED" -> "COMP" to "COMP"
  else -> throw IllegalArgumentException("Unknown DPS court appearance status: $this")
}
