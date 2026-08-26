package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.NomisMovementId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.findMatches
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.findMissing
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.MismatchedPrisonerTransferIds.Type.MISSING_MAPPING_SCHEDULE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.MismatchedPrisonerTransferIds.Type.MISSING_MAPPING_SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.MismatchedPrisonerTransferIds.Type.MISSING_MAPPING_UNSCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.MismatchedPrisonerTransferIds.Type.SCHEDULE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.MismatchedPrisonerTransferIds.Type.SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.MismatchedPrisonerTransferIds.Type.UNSCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransferMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.ReconciliationResponse
import java.util.UUID

@Service
class TransferScheduleReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApi: TransferSchedulerNomisApiService,
  private val dpsApi: TransferSchedulerDpsApiService,
  private val mappingApi: TransferSchedulerMappingApiService,
  private val nomisPrisonerApi: NomisApiService,
  @param:Value($$"${reports.transfer-scheduler.active-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.transfer-scheduler.active-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_TRANSFER_SCHEDULER = "transfer-scheduler-reconciliation"
  }

  suspend fun generateTransferSchedulerReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_TRANSFER_SCHEDULER-requested",
      mapOf(),
    )

    runCatching { generateTransferSchedulerReconciliationReport() }
      .onSuccess {
        log.info("Transfer scheduler reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_TRANSFER_SCHEDULER-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_TRANSFER_SCHEDULER-report", mapOf("success" to "false"))
        log.error("Transfer scheduler all prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerId>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateTransferSchedulerReconciliationReport(): ReconciliationResult<PrisonerId> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonersMatch,
    nextPage = ::getNextPrisonersForPage,
  )

  private suspend fun getNextPrisonersForPage(lastOffenderId: Long): ReconciliationPageResult<PrisonerId> = runCatching {
    nomisPrisonerApi.getAllPrisoners(fromId = lastOffenderId, pageSize = pageSize)
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_TRANSFER_SCHEDULER-mismatch-page-error",
      mapOf(
        "offenderId" to lastOffenderId.toString(),
      ),
    )
    log.error("Unable to match entire page of offenders from offender ID: $lastOffenderId", it)
  }
    .map {
      ReconciliationSuccessPageResult(
        ids = it.prisonerIds,
        last = it.lastOffenderId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from offender ID: $lastOffenderId, with $pageSize offenders") }

  suspend fun checkPrisonersMatch(prisonerId: PrisonerId): PrisonerId? = runCatching {
    checkPrisonersMatch(prisonerId.offenderNo).takeIf { it.isNotEmpty() }
      ?.let { prisonerId }
  }.onFailure {
    log.error("Unable to match transfers for prisoner ${prisonerId.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_TRANSFER_SCHEDULER-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
    )
  }.getOrNull()

  suspend fun checkPrisonersMatch(offenderNo: String): List<MismatchedPrisonerTransfer> = withContext(Dispatchers.Unconfined) {
    val nomisTransfers = async { nomisApi.getOffenderTransferMovementsOrNull(offenderNo) }
    val dpsTransfers = async { dpsApi.getTransferSchedulerReconciliation(offenderNo) }
    val mappings = async { mappingApi.getMappings(offenderNo) }

    checkPrisonersMatch(offenderNo, nomisTransfers.await(), dpsTransfers.await(), mappings.await())
  }

  private fun checkPrisonersMatch(
    offenderNo: String,
    nomisTransfers: OffenderTransferMovementsResponse?,
    dpsTransfers: ReconciliationResponse,
    mappings: TransferSchedulerPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerTransfer> {
    if (nomisTransfers == null) {
      throw IllegalStateException("Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?")
    }

    val mismatchedEntities = findMismatchedEntities(offenderNo, nomisTransfers, dpsTransfers, mappings)
    mismatchedEntities.forEach {
      telemetryClient.trackEvent(
        "$TELEMETRY_TRANSFER_SCHEDULER-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "type" to it.type,
          "nomisCount" to it.nomisCount.toString(),
          "dpsCount" to it.dpsCount.toString(),
          "unexpected-nomis-ids" to it.unexpectedNomisIds,
          "unexpected-dps-ids" to it.unexpectedDpsIds,
        ),
      )
    }

    val missingMappings = findMissingMappings(offenderNo, nomisTransfers, dpsTransfers, mappings)
    missingMappings.forEach {
      telemetryClient.trackEvent(
        "$TELEMETRY_TRANSFER_SCHEDULER-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "type" to it.type,
          "nomisCount" to it.nomisCount.toString(),
          "dpsCount" to it.dpsCount.toString(),
          "unexpected-nomis-ids" to it.unexpectedNomisIds,
          "unexpected-dps-ids" to it.unexpectedDpsIds,
        ),
      )
    }

    return mismatchedEntities + missingMappings
  }

  private fun findMismatchedEntities(
    offenderNo: String,
    nomisTransfers: OffenderTransferMovementsResponse,
    dpsTransfers: ReconciliationResponse,
    mappings: TransferSchedulerPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerTransferIds> {
    val mismatches = mutableListOf<MismatchedPrisonerTransferIds>()

    // Check for schedules only in 1 system
    val nomisScheduleIds = nomisTransfers.scheduleIds()
    val dpsScheduleIds = dpsTransfers.scheduleIds()
    if (nomisScheduleIds.size != dpsScheduleIds.size) {
      mismatches.add(
        MismatchedPrisonerTransferIds(
          offenderNo = offenderNo,
          type = SCHEDULE,
          nomisCount = nomisScheduleIds.size,
          dpsCount = dpsScheduleIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisSchedules(nomisScheduleIds, dpsScheduleIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsSchedules(dpsScheduleIds, nomisScheduleIds).toString(),
        ),
      )
    }

    // Check for scheduled movements only in 1 system
    val nomisScheduledMovementIds = nomisTransfers.scheduledMovementIds()
    val dpsScheduledMovementIds = dpsTransfers.scheduledMovementIds()
    if (nomisScheduledMovementIds.size != dpsScheduledMovementIds.size) {
      mismatches.add(
        MismatchedPrisonerTransferIds(
          offenderNo = offenderNo,
          type = SCHEDULED_MOVEMENT,
          nomisCount = nomisScheduledMovementIds.size,
          dpsCount = dpsScheduledMovementIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisScheduledMovementIds, dpsScheduledMovementIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsScheduledMovementIds, nomisScheduledMovementIds).toString(),
        ),
      )
    }

    // Check for unscheduled movements only in 1 system
    val nomisUnscheduledMovementIds = nomisTransfers.unscheduledMovementIds()
    val dpsUnscheduledMovementIds = dpsTransfers.unscheduledMovementIds()
    if (nomisUnscheduledMovementIds.size != dpsUnscheduledMovementIds.size) {
      mismatches.add(
        MismatchedPrisonerTransferIds(
          offenderNo = offenderNo,
          type = UNSCHEDULED_MOVEMENT,
          nomisCount = nomisUnscheduledMovementIds.size,
          dpsCount = dpsUnscheduledMovementIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisUnscheduledMovementIds, dpsUnscheduledMovementIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsUnscheduledMovementIds, nomisUnscheduledMovementIds).toString(),
        ),
      )
    }

    return mismatches
  }

  private fun findMissingMappings(
    offenderNo: String,
    nomisTransfers: OffenderTransferMovementsResponse,
    dpsTransfers: ReconciliationResponse,
    mappings: TransferSchedulerPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerTransferIds> {
    val mismatches = mutableListOf<MismatchedPrisonerTransferIds>()

    // Check for missing schedule mappings
    val nomisScheduleIds = nomisTransfers.scheduleIds()
    val dpsScheduleIds = dpsTransfers.scheduleIds()
    val matchedScheduleIds = mappings.matchingSchedules(nomisScheduleIds, dpsScheduleIds)
    if (matchedScheduleIds.size != nomisScheduleIds.size || matchedScheduleIds.size != dpsScheduleIds.size) {
      val missingNomisIds = nomisScheduleIds - matchedScheduleIds.map { it.first }.toSet()
      val missingDpsIds = dpsScheduleIds - matchedScheduleIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerTransferIds(
          offenderNo = offenderNo,
          type = MISSING_MAPPING_SCHEDULE,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    // Check for missing scheduled movement mappings
    val nomisScheduledMovementIds = nomisTransfers.scheduledMovementIds()
    val dpsScheduledMovementIds = dpsTransfers.scheduledMovementIds()
    val matchedScheduleMovementIds = mappings.matchingMovements(nomisScheduledMovementIds, dpsScheduledMovementIds)
    if (matchedScheduleMovementIds.size != nomisScheduledMovementIds.size || matchedScheduleMovementIds.size != dpsScheduledMovementIds.size) {
      val missingNomisIds = nomisScheduledMovementIds - matchedScheduleMovementIds.map { it.first }.toSet()
      val missingDpsIds = dpsScheduledMovementIds - matchedScheduleMovementIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerTransferIds(
          offenderNo = offenderNo,
          type = MISSING_MAPPING_SCHEDULED_MOVEMENT,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    // Check for missing unscheduled movement mappings
    val nomisUnscheduledMovementIds = nomisTransfers.unscheduledMovementIds()
    val dpsUnscheduledMovementIds = dpsTransfers.unscheduledMovementIds()
    val matchedUnscheduledMovementIds = mappings.matchingMovements(nomisUnscheduledMovementIds, dpsUnscheduledMovementIds)
    if (matchedUnscheduledMovementIds.size != nomisUnscheduledMovementIds.size || matchedUnscheduledMovementIds.size != dpsUnscheduledMovementIds.size) {
      val missingNomisIds = nomisUnscheduledMovementIds - matchedUnscheduledMovementIds.map { it.first }.toSet()
      val missingDpsIds = dpsUnscheduledMovementIds - matchedUnscheduledMovementIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerTransferIds(
          offenderNo = offenderNo,
          type = MISSING_MAPPING_UNSCHEDULED_MOVEMENT,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    return mismatches
  }

  private fun TransferSchedulerPrisonerMappingIdsDto.unexpectedNomisSchedules(
    nomisScheduleIds: List<Long>,
    dpsScheduleIds: List<UUID>,
  ) = findMissing(nomisScheduleIds, dpsScheduleIds) { nomisId ->
    this.schedules.find { it.nomisEventId == nomisId }?.dpsTransferScheduleId
  }

  private fun TransferSchedulerPrisonerMappingIdsDto.unexpectedDpsSchedules(
    dpsScheduleIds: List<UUID>,
    nomisScheduleIds: List<Long>,
  ) = findMissing(dpsScheduleIds, nomisScheduleIds) { dpsId ->
    this.schedules.find { it.dpsTransferScheduleId == dpsId }?.nomisEventId
  }

  private fun TransferSchedulerPrisonerMappingIdsDto.unexpectedNomisMovements(
    nomisMovementIds: List<NomisMovementId>,
    dpsMovementIds: List<UUID>,
  ) = findMissing(nomisMovementIds, dpsMovementIds) { nomisId ->
    this.movements.find { it.nomisBookingId == nomisId.bookingId && it.nomisMovementSeq == nomisId.sequence }?.dpsTransferMovementId
  }

  private fun TransferSchedulerPrisonerMappingIdsDto.unexpectedDpsMovements(
    dpsMovementIds: List<UUID>,
    nomisMovementIds: List<NomisMovementId>,
  ) = findMissing(dpsMovementIds, nomisMovementIds) { dpsId ->
    this.movements.find { it.dpsTransferMovementId == dpsId }?.let { NomisMovementId(it.nomisBookingId, it.nomisMovementSeq) }
  }

  private fun TransferSchedulerPrisonerMappingIdsDto.matchingSchedules(
    nomisScheduleIds: List<Long>,
    dpsScheduleIds: List<UUID>,
  ) = findMatches(nomisScheduleIds, dpsScheduleIds) { nomisId ->
    this.schedules.find { it.nomisEventId == nomisId }?.dpsTransferScheduleId
  }

  private fun TransferSchedulerPrisonerMappingIdsDto.matchingMovements(
    nomisMovementIds: List<NomisMovementId>,
    dpsMovementIds: List<UUID>,
  ) = findMatches(nomisMovementIds, dpsMovementIds) { nomisId ->
    this.movements.find { it.nomisBookingId == nomisId.bookingId && it.nomisMovementSeq == nomisId.sequence }?.dpsTransferMovementId
  }

  private fun OffenderTransferMovementsResponse.scheduleIds() = bookings.flatMap { it.transferSchedules }.map { it.schedule.eventId }
  private fun OffenderTransferMovementsResponse.scheduledMovementIds() = bookings.flatMap { it.transferSchedules }.mapNotNull { it.movement }.map { NomisMovementId(it.bookingId, it.sequence) }
  private fun OffenderTransferMovementsResponse.unscheduledMovementIds() = bookings.flatMap { it.unscheduledTransferMovements }.map { NomisMovementId(it.bookingId, it.sequence) }
  private fun ReconciliationResponse.scheduleIds() = transfers.map { it.transfer.dpsId!! }
  private fun ReconciliationResponse.scheduledMovementIds() = transfers.mapNotNull { it.movement }.map { it.dpsId!! }
  private fun ReconciliationResponse.unscheduledMovementIds() = unscheduledMovements.map { it.dpsId!! }
}

abstract class MismatchedPrisonerTransfer(
  val offenderNo: String,
  val type: String,
)

class MismatchedPrisonerTransferIds(
  offenderNo: String,
  type: Type,
  val nomisCount: Int,
  val dpsCount: Int,
  val unexpectedNomisIds: String,
  val unexpectedDpsIds: String,
) : MismatchedPrisonerTransfer(offenderNo, type.name) {
  enum class Type {
    SCHEDULE,
    SCHEDULED_MOVEMENT,
    UNSCHEDULED_MOVEMENT,
    MISSING_MAPPING_SCHEDULE,
    MISSING_MAPPING_SCHEDULED_MOVEMENT,
    MISSING_MAPPING_UNSCHEDULED_MOVEMENT,
  }
}
