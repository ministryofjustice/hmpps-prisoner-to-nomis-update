package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerRetryService.Companion.MappingTypes.SCHEDULE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.track
import java.util.*

// These events are ignored if there is a RaS external reference on the event (we expect this to be true for all events soon)
val ignoreSentencingEvents = listOf(
  "person.court-appearance.scheduled",
  "person.court-appearance.recorded",
  "person.court-appearance.cancelled",
)

@Service
class CourtSchedulerAppearanceService(
  private val mappingApi: CourtSchedulerMappingApiService,
  private val dpsApi: CourtSchedulerDpsApiService,
  private val nomisApi: CourtSchedulerNomisApiService,
  private val retryQueueService: CourtSchedulerRetryQueueService,
  override val telemetryClient: TelemetryClient,
  private val features: CourtSchedulerFeatureSwitches,
) : TelemetryEnabled {
  companion object {
    private val TELEMETRY_KEY = "court-scheduler-schedule"
    private val TELEMETRY_KEY_CREATE = "$TELEMETRY_KEY-create"
    private val TELEMETRY_KEY_UPDATE = "$TELEMETRY_KEY-update"
    private val TELEMETRY_KEY_DELETE = "$TELEMETRY_KEY-delete"

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun courtAppearanceChanged(event: CourtSchedulerEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsCourtAppearanceId = event.additionalInformation.id
    val externalReference = event.additionalInformation.externalReferenceUrn
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsCourtAppearanceId" to dpsCourtAppearanceId.toString(),
      "externalReferenceUrn" to externalReference.toString(),
    )

    if (event.additionalInformation.source != "DPS" || shouldIgnoreSyncEvent(externalReference, event.eventType, features.ignoreAllSentencingEvents)) {
      telemetryClient.trackEvent("$TELEMETRY_KEY-ignored", telemetryMap)
      return
    }

    courtAppearanceChanged(prisonerNumber, dpsCourtAppearanceId, telemetryMap)
  }

  suspend fun courtAppearanceChanged(
    prisonerNumber: String,
    dpsCourtAppearanceId: UUID,
    telemetryMap: MutableMap<String, String> = mutableMapOf(),
    recreate: Boolean = false,
  ) {
    var telemetryKey = TELEMETRY_KEY_CREATE
    telemetryMap["recreate"] = "$recreate"
    runCatching {
      val existingMapping = mappingApi.getCourtScheduleMapping(dpsCourtAppearanceId)
      if (existingMapping != null) {
        telemetryKey = TELEMETRY_KEY_UPDATE
      } else {
        if (recreate) throw BadRequestException("Cannot find court schedule mapping for $dpsCourtAppearanceId but recreate is true so we need an existing nomis eventId")
      }
      val dps = dpsApi.getCourtAppearance(dpsCourtAppearanceId)
      val nomis = nomisApi.upsertCourtScheduleOut(prisonerNumber, dps.toNomisUpsertRequest(existingMapping?.nomisEventId), recreate)
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

  suspend fun courtAppearanceDeleted(event: CourtSchedulerEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsCourtAppearanceId = event.additionalInformation.id
    val externalReference = event.additionalInformation.externalReferenceUrn
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsCourtAppearanceId" to dpsCourtAppearanceId.toString(),
      "externalReferenceUrn" to externalReference.toString(),
    )

    if (event.additionalInformation.source != "DPS" || shouldIgnoreSyncEvent(externalReference, event.eventType, features.ignoreAllSentencingEvents)) {
      telemetryClient.trackEvent("$TELEMETRY_KEY_DELETE-ignored", telemetryMap)
      return
    }

    track(TELEMETRY_KEY_DELETE, telemetryMap) {
      val mapping = mappingApi.getCourtScheduleMapping(dpsCourtAppearanceId)
        ?.also { telemetryMap["nomisEventId"] = it.nomisEventId.toString() }
        ?: throw CourtSchedulerSyncException("Cannot find court schedule mapping for $dpsCourtAppearanceId")
      nomisApi.deleteCourtScheduleOut(prisonerNumber, mapping.nomisEventId)
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
  val (status, returnStatus) = eventStatus.toNomisSchedulesStatus(externalCourtEventType ?: true)
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

fun String.toNomisSchedulesStatus(external: Boolean) = when {
  !external -> "EXP" to null
  this == "SCHEDULED" -> "SCH" to null
  this == "EXPIRED" -> "EXP" to null
  this == "IN_PROGRESS" -> "COMP" to "SCH"
  this == "COMPLETED" -> "COMP" to "COMP"
  else -> throw IllegalArgumentException("Unknown DPS court appearance status: $this")
}

class CourtSchedulerSyncException(message: String) : RuntimeException(message)

// Eventually we will ignore all remand and sentencing events - which are any with an external reference
// To assist integration between court scheduler and remand and sentencing, it's currently a bit more complicated and we always ignore some events but others are behind a feature switch
internal fun shouldIgnoreSyncEvent(externalReferenceUrn: String?, eventType: String, ignoreAllEvents: Boolean): Boolean {
  // No external reference means this is owned by the court scheduler, so we never ignore
  if (externalReferenceUrn == null) return false

  // The event type is one that we always ignore
  if (ignoreSentencingEvents.contains(eventType)) return true

  // The ignoreAllSentencingEvents feature is enabled, so we ignore all events
  if (ignoreAllEvents) return true

  // We cannot find a reason to ignore the event, so don't ignore it
  return false
}
