package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTapMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTapScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.util.*

@Service
class TapActivePrisonersReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: TapNomisApiService,
  private val dpsApiService: TapDpsApiService,
  private val nomisPrisonerApiService: NomisApiService,
  private val mappingService: TapMappingApiService,
  @param:Value($$"${reports.temporary-absences.active-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.temporary-absences.active-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_ACTIVE_TAPS = "temporary-absences-active-reconciliation"
  }

  suspend fun generateTapActivePrisonersReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_TAPS-requested",
      mapOf(),
    )

    runCatching { generateTapActivePrisonersReconciliationReport() }
      .onSuccess {
        log.info("Temporary absences active prisoners reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_ACTIVE_TAPS-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_ACTIVE_TAPS-report", mapOf("success" to "false"))
        log.error("Temporary absences all prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerIds>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateTapActivePrisonersReconciliationReport(): ReconciliationResult<PrisonerIds> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonerTapsMatch,
    nextPage = ::getNextBookingsForPage,
  )

  private suspend fun getNextBookingsForPage(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = runCatching {
    nomisPrisonerApiService.getAllLatestBookings(
      lastBookingId = lastBookingId,
      activeOnly = true,
      pageSize = pageSize,
    )
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_TAPS-mismatch-page-error",
      mapOf(
        "booking" to lastBookingId.toString(),
      ),
    )
    log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
  }
    .map {
      ReconciliationSuccessPageResult(
        ids = it.prisonerIds,
        last = it.lastBookingId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from booking: $lastBookingId, with $pageSize offenders") }

  suspend fun checkPrisonerTapsMatch(prisonerIds: PrisonerIds): PrisonerIds? = runCatching {
    checkPrisonerTapsMatch(prisonerIds.offenderNo).takeIf { it.isNotEmpty() }
      ?.let { prisonerIds }
  }.onFailure {
    log.error("Unable to match temporary absences for prisoner ${prisonerIds.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_TAPS-mismatch-error",
      mapOf(
        "offenderNo" to prisonerIds.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
    )
  }.getOrNull()

  suspend fun checkPrisonerTapsMatch(offenderNo: String, suppressTelemetry: Boolean = false): List<MismatchPrisonerTaps> = withContext(Dispatchers.Unconfined) {
    val nomisTaps = async { nomisApiService.getAllOffenderTapsOrNull(offenderNo) }
    val dpsTaps = async { dpsApiService.getTapReconciliationDetail(offenderNo) }
    val mappings = async { mappingService.getTapMappingIds(offenderNo) }

    checkTapsMatch(
      offenderNo = offenderNo,
      dpsTaps = dpsTaps.await(),
      nomisTaps = nomisTaps.await(),
      mappings = mappings.await(),
      suppressTelemetry = suppressTelemetry,
    )
  }

  private fun checkTapsMatch(
    offenderNo: String,
    dpsTaps: PersonTapDetail,
    nomisTaps: OffenderTapsResponse?,
    mappings: TapPrisonerMappingIdsDto,
    suppressTelemetry: Boolean,
  ): List<MismatchPrisonerTaps> {
    if (nomisTaps == null) {
      throw IllegalStateException("Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?")
    }

    val mismatchedCounts = findMismatchedCounts(offenderNo, nomisTaps, dpsTaps, mappings)
    if (!suppressTelemetry) {
      mismatchedCounts.forEach {
        telemetryClient.trackEvent(
          "$TELEMETRY_ACTIVE_TAPS-mismatch",
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
    }

    val mismatchedSchedulesOut = findMismatchedOccurrences(offenderNo, nomisTaps, dpsTaps, mappings)
    if (!suppressTelemetry) {
      mismatchedSchedulesOut.forEach {
        telemetryClient.trackEvent(
          "$TELEMETRY_ACTIVE_TAPS-mismatch",
          mapOf(
            "offenderNo" to offenderNo,
            "nomisEventId" to "${it.nomisEventId}",
            "dpsOccurrenceId" to "${it.dpsOccurrenceId}",
            "type" to it.type,
          ),
        )
      }
    }

    return mismatchedCounts + mismatchedSchedulesOut
  }

  // Checks each NOMIS ID for a mapping to a real DPS ID, and vice versa. Any not found are returned
  private fun findMismatchedCounts(
    offenderNo: String,
    nomisIds: OffenderTapsResponse,
    dpsIds: PersonTapDetail,
    mappings: TapPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerTapIds> {
    val mismatchedDetails = mutableListOf<MismatchedPrisonerTapIds>()

    val nomisApplicationIds = nomisIds.bookings.flatMap { it.tapApplications.map { it.tapApplicationId } }
    val dpsAuthorisationIds = dpsIds.scheduledAbsences.map { it.id }
    if (nomisApplicationIds.size != dpsAuthorisationIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.AUTHORISATIONS,
          nomisCount = nomisApplicationIds.size,
          dpsCount = dpsAuthorisationIds.size,
          unexpectedNomisIds = mappings.unexpectedApplications(nomisApplicationIds, dpsAuthorisationIds).toString(),
          unexpectedDpsIds = mappings.unexpectedAuthorisations(dpsAuthorisationIds, nomisApplicationIds).toString(),
        ),
      )
    }

    val nomisScheduleOutIds = nomisIds.bookings.flatMap { it.tapApplications.flatMap { it.taps.mapNotNull { it.tapScheduleOut?.eventId } } }
    val dpsOccurrenceIds = dpsIds.scheduledAbsences.flatMap { it.occurrences.map { it.id } }
    if (nomisScheduleOutIds.size != dpsOccurrenceIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.OCCURRENCES,
          nomisCount = nomisScheduleOutIds.size,
          dpsCount = dpsOccurrenceIds.size,
          unexpectedNomisIds = mappings.unexpectedScheduledOut(nomisScheduleOutIds, dpsOccurrenceIds).toString(),
          unexpectedDpsIds = mappings.unexpectedOccurrences(dpsOccurrenceIds, nomisScheduleOutIds).toString(),
        ),
      )
    }

    val nomisScheduledMovementOutIds = nomisIds.bookings.flatMap { booking -> booking.tapApplications.flatMap { it.taps.mapNotNull { it.tapMovementOut?.let { NomisMovementId(booking.bookingId, it.sequence) } } } }
    val dpsScheduledOutIds = dpsIds.scheduledAbsences.flatMap { it.occurrences.flatMap { it.movements.filter { it.direction == ReconciliationMovement.Direction.OUT }.map { it.id } } }
    if (nomisScheduledMovementOutIds.size != dpsScheduledOutIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.SCHEDULED_OUT,
          nomisCount = nomisScheduledMovementOutIds.size,
          dpsCount = dpsScheduledOutIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisScheduledMovementOutIds, dpsScheduledOutIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsScheduledOutIds, nomisScheduledMovementOutIds).toString(),
        ),
      )
    }

    val nomisScheduledMovementInIds = nomisIds.bookings.flatMap { booking -> booking.tapApplications.flatMap { it.taps.mapNotNull { it.tapMovementIn?.let { NomisMovementId(booking.bookingId, it.sequence) } } } }
    val dpsScheduledInIds = dpsIds.scheduledAbsences.flatMap { it.occurrences.flatMap { it.movements.filter { it.direction == ReconciliationMovement.Direction.IN }.map { it.id } } }
    if (nomisScheduledMovementInIds.size != dpsScheduledInIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.SCHEDULED_IN,
          nomisCount = nomisScheduledMovementInIds.size,
          dpsCount = dpsScheduledInIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisScheduledMovementInIds, dpsScheduledInIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsScheduledInIds, nomisScheduledMovementInIds).toString(),
        ),
      )
    }

    val nomisUnscheduledMovementOutIds = nomisIds.bookings.flatMap { booking -> booking.unscheduledTapMovementOuts.map { NomisMovementId(booking.bookingId, it.sequence) } }
    val dpsUnscheduledOutIds = dpsIds.unscheduledMovements.filter { it.direction == ReconciliationMovement.Direction.OUT }.map { it.id }
    if (nomisUnscheduledMovementOutIds.size != dpsUnscheduledOutIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.UNSCHEDULED_OUT,
          nomisCount = nomisUnscheduledMovementOutIds.size,
          dpsCount = dpsUnscheduledOutIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisUnscheduledMovementOutIds, dpsUnscheduledOutIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsUnscheduledOutIds, nomisUnscheduledMovementOutIds).toString(),
        ),
      )
    }

    val nomisUnscheduledMovementInIds = nomisIds.bookings.flatMap { booking -> booking.unscheduledTapMovementIns.map { NomisMovementId(booking.bookingId, it.sequence) } }
    val dpsUnscheduledInIds = dpsIds.unscheduledMovements.filter { it.direction == ReconciliationMovement.Direction.IN }.map { it.id }
    if (nomisUnscheduledMovementInIds.size != dpsUnscheduledInIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.UNSCHEDULED_IN,
          nomisCount = nomisUnscheduledMovementInIds.size,
          dpsCount = dpsUnscheduledInIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisUnscheduledMovementInIds, dpsUnscheduledInIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsUnscheduledInIds, nomisUnscheduledMovementInIds).toString(),
        ),
      )
    }

    /*
     * Check for missing schedule mappings.
     *
     * Usually missing mappings would cause sync problems and we'd spot them there. However, after a merge we could end
     * up missing mappings from old bookings. If these old bookings are then moved to another prisoner we only move
     * records which have mappings - history would be corrupted. So we attempt to spot this class of error early by
     * checking the schedule mappings. If this proves insufficient, we may also add checks for applications and movements.
     */
    val matchedIds = mappings.matchingSchedulesOut(nomisScheduleOutIds, dpsOccurrenceIds)
    if (nomisScheduleOutIds.size != matchedIds.size || dpsOccurrenceIds.size != matchedIds.size) {
      val missingNomisIds = nomisScheduleOutIds - matchedIds.map { it.first }.toSet()
      val missingDpsIds = dpsOccurrenceIds - matchedIds.map { it.second }.toSet()
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.MISSING_SCHEDULE_MAPPINGS,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = missingNomisIds.toString(),
          unexpectedDpsIds = missingDpsIds.toString(),
        ),
      )
    }

    return mismatchedDetails
  }

  private fun TapPrisonerMappingIdsDto.unexpectedApplications(
    nomisApplicationIds: List<Long>,
    dpsAuthorisationIds: List<UUID>,
  ) = findMissing(nomisApplicationIds, dpsAuthorisationIds) { applicationId ->
    applications.find { applicationId == it.nomisApplicationId }?.dpsAuthorisationId
  }

  private fun TapPrisonerMappingIdsDto.unexpectedAuthorisations(
    dpsAuthorisationIds: List<UUID>,
    nomisApplicationIds: List<Long>,
  ) = findMissing(dpsAuthorisationIds, nomisApplicationIds) { dpsId ->
    applications.find { dpsId == it.dpsAuthorisationId }?.nomisApplicationId
  }

  private fun TapPrisonerMappingIdsDto.unexpectedScheduledOut(
    nomisScheduledOutIds: List<Long>,
    dpsOccurrenceIds: List<UUID>,
  ) = findMissing(nomisScheduledOutIds, dpsOccurrenceIds) { nomisId ->
    schedules.find { nomisId == it.nomisEventId }?.dpsOccurrenceId
  }

  private fun TapPrisonerMappingIdsDto.unexpectedOccurrences(
    dpsOccurrenceIds: List<UUID>,
    nomisScheduledOutIds: List<Long>,
  ) = findMissing(dpsOccurrenceIds, nomisScheduledOutIds) { dpsId ->
    schedules.find { dpsId == it.dpsOccurrenceId }?.nomisEventId
  }

  private fun TapPrisonerMappingIdsDto.unexpectedNomisMovements(
    nomisMovementIds: List<NomisMovementId>,
    dpsMovementIds: List<UUID>,
  ) = findMissing(nomisMovementIds, dpsMovementIds) { nomisId ->
    this.movements.find { nomisId.bookingId == it.nomisBookingId && nomisId.sequence == it.nomisMovementSeq }?.dpsMovementId
  }

  private fun TapPrisonerMappingIdsDto.unexpectedDpsMovements(
    dpsMovementIds: List<UUID>,
    nomisMovementIds: List<NomisMovementId>,
  ) = findMissing(dpsMovementIds, nomisMovementIds) { dpsId ->
    movements.find { dpsId == it.dpsMovementId }?.let { NomisMovementId(it.nomisBookingId, it.nomisMovementSeq) }
  }

  private fun findMismatchedOccurrences(
    offenderNo: String,
    nomis: OffenderTapsResponse,
    dps: PersonTapDetail,
    mappings: TapPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerTapOccurrenceDetails> {
    val nomisScheduleOutIds = nomis.bookings.flatMap { it.tapApplications.flatMap { it.taps.mapNotNull { it.tapScheduleOut?.eventId } } }
    val dpsOccurrenceIds = dps.scheduledAbsences.flatMap { it.occurrences.map { it.id } }

    val mismatches = mutableListOf<MismatchedPrisonerTapOccurrenceDetails>()
    mappings.matchingSchedulesOut(nomisScheduleOutIds, dpsOccurrenceIds)
      .forEach { (nomisEventId, dpsOccurrenceId) ->
        val (nomisScheduleOut, nomisMovementOut) = nomis.findNomisTap(nomisEventId)
        val dpsOccurrence = dps.findOccurrence(dpsOccurrenceId)
        fun mismatch(type: MismatchedPrisonerTapOccurrenceDetails.Type, nomisValue: String, dpsValue: String) = MismatchedPrisonerTapOccurrenceDetails(offenderNo, type, nomisEventId, dpsOccurrenceId, nomisValue, dpsValue)

        // reason code must match
        if (dpsOccurrence.reasonCode != nomisScheduleOut.eventSubType) {
          mismatches.add(mismatch(MismatchedPrisonerTapOccurrenceDetails.Type.OCCURRENCE_REASON, nomisScheduleOut.eventSubType, dpsOccurrence.reasonCode))
        }

        // start time must match
        if (dpsOccurrence.start != nomisScheduleOut.startTime) {
          mismatches.add(mismatch(MismatchedPrisonerTapOccurrenceDetails.Type.OCCURRENCE_START_TIME, "${nomisScheduleOut.startTime}", "${dpsOccurrence.start}"))
        }

        // end time must match
        if (dpsOccurrence.end != nomisScheduleOut.returnTime) {
          mismatches.add(mismatch(MismatchedPrisonerTapOccurrenceDetails.Type.OCCURRENCE_END_TIME, "${nomisScheduleOut.returnTime}", "${dpsOccurrence.end}"))
        }

        // postcode must match as long as schedule not in the past
        val nomisPostcode = nomisMovementOut?.let { nomisMovementOut.toAddressPostcode } ?: nomisScheduleOut.toAddressPostcode
        if (nomisScheduleOut.startTime.toLocalDate() >= LocalDate.now() && dpsOccurrence.location?.postcode != nomisPostcode) {
          mismatches.add(mismatch(MismatchedPrisonerTapOccurrenceDetails.Type.OCCURRENCE_POSTCODE, "${nomisScheduleOut.toAddressPostcode}", "${dpsOccurrence.location?.postcode}"))
        }
      }

    return mismatches
  }

  private fun TapPrisonerMappingIdsDto.matchingSchedulesOut(
    nomisScheduledOutIds: List<Long>,
    dpsOccurrenceIds: List<UUID>,
  ) = findMatches(nomisScheduledOutIds, dpsOccurrenceIds) { nomisId ->
    schedules.find { nomisId == it.nomisEventId }?.dpsOccurrenceId
  }

  private data class NomisDetails(
    val scheduledOut: BookingTapScheduleOut,
    val movementOut: BookingTapMovementOut?,
  )

  private fun OffenderTapsResponse.findNomisTap(eventId: Long): NomisDetails = bookings.flatMap { booking ->
    booking.tapApplications.flatMap { application ->
      application.taps.mapNotNull { tap ->
        if (tap.tapScheduleOut?.eventId == eventId) {
          NomisDetails(tap.tapScheduleOut, tap.tapMovementOut)
        } else {
          null
        }
      }
    }
  }
    .firstOrNull()
    ?: throw IllegalStateException("Unable to find schedule out for eventId=$eventId despite having matched it earlier. Has there been a merge or move booking?")

  private fun PersonTapDetail.findOccurrence(occurrenceId: UUID): ReconciliationOccurrence = scheduledAbsences.flatMap { absence ->
    absence.occurrences
  }
    .firstOrNull { occurrence -> occurrence.id == occurrenceId }
    ?: throw IllegalStateException("Unable to find occurrence for occurrenceId=$occurrenceId despite having matched it earlier. Has there been a merge or move booking?")
}

abstract class MismatchPrisonerTaps(
  val offenderNo: String,
  val type: String,
)

class MismatchedPrisonerTapIds(
  offenderNo: String,
  type: Type,
  val nomisCount: Int,
  val dpsCount: Int,
  val unexpectedNomisIds: String,
  val unexpectedDpsIds: String,
) : MismatchPrisonerTaps(offenderNo, type.name) {
  enum class Type {
    AUTHORISATIONS,
    MISSING_SCHEDULE_MAPPINGS,
    OCCURRENCES,
    SCHEDULED_OUT,
    SCHEDULED_IN,
    UNSCHEDULED_OUT,
    UNSCHEDULED_IN,
  }
}

class MismatchedPrisonerTapOccurrenceDetails(
  offenderNo: String,
  type: Type,
  val nomisEventId: Long,
  val dpsOccurrenceId: UUID,
  val nomisValue: String,
  val dpsValue: String,
) : MismatchPrisonerTaps(offenderNo, type.name) {
  enum class Type {
    OCCURRENCE_REASON,
    OCCURRENCE_START_TIME,
    OCCURRENCE_END_TIME,
    OCCURRENCE_POSTCODE,
  }
}
