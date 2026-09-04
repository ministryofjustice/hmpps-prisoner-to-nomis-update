package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerRetryService.Companion.MappingTypes.SCHEDULE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleWaitlist
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.createMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncTransfer
import java.util.*

@Service
class TransferSchedulerScheduleService(
  private val mappingApi: TransferSchedulerMappingApiService,
  private val dpsApi: TransferSchedulerDpsApiService,
  private val nomisApi: TransferSchedulerNomisApiService,
  private val retryQueueService: TransferSchedulerRetryQueueService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  companion object {
    private val TELEMETRY_KEY = "transfer-scheduler-schedule"
    private val TELEMETRY_KEY_CREATE = "$TELEMETRY_KEY-create"
    private val TELEMETRY_KEY_UPDATE = "$TELEMETRY_KEY-update"

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun transferScheduleChanged(event: TransferSchedulerEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsTransferScheduleId = event.additionalInformation.id
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsTransferScheduleId" to dpsTransferScheduleId.toString(),
    )
    var telemetryKey = TELEMETRY_KEY_CREATE

    if (event.additionalInformation.source != "DPS") {
      telemetryClient.trackEvent("${TELEMETRY_KEY}-ignored", telemetryMap)
      return
    }

    runCatching {
      val existingMapping = mappingApi.getTransferScheduleMapping(dpsTransferScheduleId)
      if (existingMapping != null) {
        telemetryKey = TELEMETRY_KEY_UPDATE
      }
      val dps = dpsApi.getTransferSchedule(dpsTransferScheduleId)
      val nomis = nomisApi.upsertTransferSchedule(prisonerNumber, dps.toNomisUpsertRequest(existingMapping?.nomisEventId))
        .also {
          telemetryMap["bookingId"] = it.bookingId.toString()
          telemetryMap["nomisEventId"] = it.eventId.toString()
        }

      if (existingMapping == null) {
        createTransferScheduleMapping(prisonerNumber, nomis.bookingId, nomis.eventId, dpsTransferScheduleId, telemetryMap)
      } else {
        telemetryClient.trackEvent("$telemetryKey-success", telemetryMap)
      }
    }
      .onFailure {
        telemetryMap["error"] = it.message ?: "Unknown error"
        telemetryClient.trackEvent("$telemetryKey-error", telemetryMap)
        throw it
      }
  }

  private suspend fun createTransferScheduleMapping(
    prisonerNumber: String,
    bookingId: Long,
    eventId: Long,
    dpsTransferScheduleId: UUID,
    telemetryMap: MutableMap<String, String>,
  ) = TransferScheduleMappingDto(prisonerNumber, bookingId, eventId, dpsTransferScheduleId, TransferScheduleMappingDto.MappingType.DPS_CREATED)
    .also {
      createMapping(
        it,
        telemetryClient,
        { createTransferScheduleMapping(it, telemetryMap) },
        telemetryMap,
        retryQueueService,
        SCHEDULE.entityName,
        log,
        failureSuffix = "error",
        failureReasonKey = "error",
      )
    }

  suspend fun createTransferScheduleMapping(message: CreateMappingRetryMessage<TransferScheduleMappingDto>) {
    createTransferScheduleMapping(message.mapping, message.telemetryAttributes)
  }

  suspend fun createTransferScheduleMapping(mapping: TransferScheduleMappingDto, telemetry: Map<String, String>) {
    mappingApi.createTransferScheduleMapping(mapping).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_KEY_CREATE}-success",
        telemetry,
      )
    }
  }
}

private fun SyncTransfer.toNomisUpsertRequest(eventId: Long?) = UpsertTransferScheduleOut(
  eventId = eventId,
  eventSubType = schedule!!.eventSubType,
  eventStatus = schedule.eventStatus,
  fromPrison = schedule.agyLocId,
  startTime = schedule.start,
  comment = schedule.commentText,
  toPrison = schedule.toAgyLocId,
  escortCode = schedule.escortCode,
  waitlist = waitlist?.let {
    UpsertTransferScheduleWaitlist(
      requestDate = it.requestDate,
      status = waitlist.waitListStatus,
      priority = waitlist.transferPriority,
      approvedUserName = waitlist.approvedUsername,
      comment = waitlist.commentText1,
    )
  },
)
