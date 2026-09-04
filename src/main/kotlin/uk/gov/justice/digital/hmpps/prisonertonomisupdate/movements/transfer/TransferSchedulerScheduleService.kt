package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleWaitlist
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncTransfer

@Service
class TransferSchedulerScheduleService(
  private val mappingApi: TransferSchedulerMappingApiService,
  private val dpsApi: TransferSchedulerDpsApiService,
  private val nomisApi: TransferSchedulerNomisApiService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  companion object {
    private val TELEMETRY_KEY = "transfer-scheduler-schedule"
    private val TELEMETRY_KEY_CREATE = "$TELEMETRY_KEY-create"

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun transferScheduleChanged(event: TransferSchedulerEvent) {
    val prisonerNumber = event.personReference.prisonerNumber()
    val dpsTransferScheduleId = event.additionalInformation.id
    val telemetryMap = mutableMapOf(
      "offenderNo" to prisonerNumber,
      "dpsTransferScheduleId" to dpsTransferScheduleId.toString(),
    )
    val telemetryKey = TELEMETRY_KEY_CREATE

    runCatching {
      val existingMapping = mappingApi.getTransferScheduleMapping(dpsTransferScheduleId)
      val dps = dpsApi.getTransferSchedule(dpsTransferScheduleId)
      val nomis = nomisApi.upsertTransferSchedule(prisonerNumber, dps.toNomisUpsertRequest())
        .also {
          telemetryMap["bookingId"] = it.bookingId.toString()
          telemetryMap["nomisEventId"] = it.eventId.toString()
        }

      val mapping = TransferScheduleMappingDto(prisonerNumber, nomis.bookingId, nomis.eventId, dpsTransferScheduleId, TransferScheduleMappingDto.MappingType.DPS_CREATED)
      mappingApi.createTransferScheduleMapping(mapping)

      telemetryClient.trackEvent("$telemetryKey-success", telemetryMap)
    }
  }
}

private fun SyncTransfer.toNomisUpsertRequest() = UpsertTransferScheduleOut(
  eventId = null,
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
      statusDate = waitlist.statusDate,
      priority = waitlist.transferPriority,
      approved = waitlist.approved,
      approvedUserName = waitlist.approvedUsername,
      comment = waitlist.commentText1,
    )
  },
)
