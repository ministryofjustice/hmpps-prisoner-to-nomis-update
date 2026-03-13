package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ScheduledTemporaryAbsence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.util.UUID

@Service
class TemporaryAbsencesActivePrisonersReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: ExternalMovementsNomisApiService,
  private val dpsApiService: ExternalMovementsDpsApiService,
  private val nomisPrisonerApiService: NomisApiService,
  private val mappingService: ExternalMovementsMappingApiService,
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

  suspend fun checkPrisonerTapsMatch(offenderNo: String): List<MismatchPrisonerTaps> = withContext(Dispatchers.Unconfined) {
    val nomisTaps = async { nomisApiService.getTemporaryAbsencesOrNull(offenderNo) }
    val dpsTaps = async { dpsApiService.getTapReconciliationDetail(offenderNo) }
    val mappings = async { mappingService.getTapMappingIds(offenderNo) }

    checkTapsMatch(
      offenderNo = offenderNo,
      dpsTaps = dpsTaps.await(),
      nomisTaps = nomisTaps.await(),
      mappings = mappings.await(),
    )
  }

  private fun checkTapsMatch(
    offenderNo: String,
    dpsTaps: PersonTapDetail,
    nomisTaps: OffenderTemporaryAbsencesResponse?,
    mappings: TemporaryAbsencesPrisonerMappingIdsDto,
  ): List<MismatchPrisonerTaps> {
    if (nomisTaps == null) {
      throw IllegalStateException("Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?")
    }

    val mismatchedCounts = findMismatchedCounts(offenderNo, nomisTaps, dpsTaps, mappings)
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

    val mismatchedSchedulesOut = findMismatchedOccurrences(offenderNo, nomisTaps, dpsTaps, mappings)
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

    return mismatchedCounts + mismatchedSchedulesOut
  }

  // Checks each NOMIS ID for a mapping to a real DPS ID, and vice versa. Any not found are returned
  private fun findMismatchedCounts(
    offenderNo: String,
    nomisIds: OffenderTemporaryAbsencesResponse,
    dpsIds: PersonTapDetail,
    mappings: TemporaryAbsencesPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerTapIds> {
    val mismatchedDetails = mutableListOf<MismatchedPrisonerTapIds>()

    val nomisApplicationIds = nomisIds.bookings.flatMap { it.temporaryAbsenceApplications.map { it.movementApplicationId } }
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

    val nomisScheduleOutIds = nomisIds.bookings.flatMap { it.temporaryAbsenceApplications.flatMap { it.absences.mapNotNull { it.scheduledTemporaryAbsence?.eventId } } }
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

    val nomisScheduledMovementOutIds = nomisIds.bookings.flatMap { booking -> booking.temporaryAbsenceApplications.flatMap { it.absences.mapNotNull { it.temporaryAbsence?.let { OffenderTemporaryAbsenceId(booking.bookingId, it.sequence) } } } }
    val dpsScheduledOutIds = dpsIds.scheduledAbsences.flatMap { it.occurrences.flatMap { it.movements.filter { it.direction == ReconciliationMovement.Direction.OUT }.map { it.id } } }
    if (nomisScheduledMovementOutIds.size != dpsScheduledOutIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.SCHEDULED_OUT,
          nomisCount = nomisScheduledMovementOutIds.size,
          dpsCount = dpsScheduledOutIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisScheduledMovementOutIds, dpsScheduledOutIds)
            .formatNomisMovementId(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsScheduledOutIds, nomisScheduledMovementOutIds)
            .toString(),
        ),
      )
    }

    val nomisScheduledMovementInIds = nomisIds.bookings.flatMap { booking -> booking.temporaryAbsenceApplications.flatMap { it.absences.mapNotNull { it.temporaryAbsenceReturn?.let { OffenderTemporaryAbsenceId(booking.bookingId, it.sequence) } } } }
    val dpsScheduledInIds = dpsIds.scheduledAbsences.flatMap { it.occurrences.flatMap { it.movements.filter { it.direction == ReconciliationMovement.Direction.IN }.map { it.id } } }
    if (nomisScheduledMovementInIds.size != dpsScheduledInIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.SCHEDULED_IN,
          nomisCount = nomisScheduledMovementInIds.size,
          dpsCount = dpsScheduledInIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisScheduledMovementInIds, dpsScheduledInIds)
            .formatNomisMovementId(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsScheduledInIds, nomisScheduledMovementInIds).toString(),
        ),
      )
    }

    val nomisUnscheduledMovementOutIds = nomisIds.bookings.flatMap { booking -> booking.unscheduledTemporaryAbsences.map { OffenderTemporaryAbsenceId(booking.bookingId, it.sequence) } }
    val dpsUnscheduledOutIds = dpsIds.unscheduledMovements.filter { it.direction == ReconciliationMovement.Direction.OUT }.map { it.id }
    if (nomisUnscheduledMovementOutIds.size != dpsUnscheduledOutIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.UNSCHEDULED_OUT,
          nomisCount = nomisUnscheduledMovementOutIds.size,
          dpsCount = dpsUnscheduledOutIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisUnscheduledMovementOutIds, dpsUnscheduledOutIds)
            .formatNomisMovementId(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsUnscheduledOutIds, nomisUnscheduledMovementOutIds)
            .toString(),
        ),
      )
    }

    val nomisUnscheduledMovementInIds = nomisIds.bookings.flatMap { booking -> booking.unscheduledTemporaryAbsenceReturns.map { OffenderTemporaryAbsenceId(booking.bookingId, it.sequence) } }
    val dpsUnscheduledInIds = dpsIds.unscheduledMovements.filter { it.direction == ReconciliationMovement.Direction.IN }.map { it.id }
    if (nomisUnscheduledMovementInIds.size != dpsUnscheduledInIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerTapIds.Type.UNSCHEDULED_IN,
          nomisCount = nomisUnscheduledMovementInIds.size,
          dpsCount = dpsUnscheduledInIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisUnscheduledMovementInIds, dpsUnscheduledInIds)
            .formatNomisMovementId(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsUnscheduledInIds, nomisUnscheduledMovementInIds)
            .toString(),
        ),
      )
    }

    return mismatchedDetails
  }

  private fun List<OffenderTemporaryAbsenceId>.formatNomisMovementId() = joinToString(prefix = "[", postfix = "]") { "${it.bookingId}_${it.sequence}" }

  // This checks each element of `sources` exists in `targets` after transformation by `findTarget`
  private fun <SOURCE, TARGET> findMissing(
    sources: List<SOURCE>,
    targets: List<TARGET>,
    findTarget: (SOURCE) -> TARGET?,
  ) = sources.map { src -> src to findTarget(src) }
    .map { (src, trg) -> src to targets.contains(trg) }
    .filter { (_, found) -> !found }
    .map { (src, _) -> src }

  private fun TemporaryAbsencesPrisonerMappingIdsDto.unexpectedApplications(
    nomisApplicationIds: List<Long>,
    dpsAuthorisationIds: List<UUID>,
  ) = findMissing(nomisApplicationIds, dpsAuthorisationIds) { applicationId ->
    applications.find { applicationId == it.nomisMovementApplicationId }?.dpsMovementApplicationId
  }

  private fun TemporaryAbsencesPrisonerMappingIdsDto.unexpectedAuthorisations(
    dpsAuthorisationIds: List<UUID>,
    nomisApplicationIds: List<Long>,
  ) = findMissing(dpsAuthorisationIds, nomisApplicationIds) { dpsId ->
    applications.find { dpsId == it.dpsMovementApplicationId }?.nomisMovementApplicationId
  }

  private fun TemporaryAbsencesPrisonerMappingIdsDto.unexpectedScheduledOut(
    nomisScheduledOutIds: List<Long>,
    dpsOccurrenceIds: List<UUID>,
  ) = findMissing(nomisScheduledOutIds, dpsOccurrenceIds) { nomisId ->
    schedules.find { nomisId == it.nomisEventId }?.dpsOccurrenceId
  }

  private fun TemporaryAbsencesPrisonerMappingIdsDto.unexpectedOccurrences(
    dpsOccurrenceIds: List<UUID>,
    nomisScheduledOutIds: List<Long>,
  ) = findMissing(dpsOccurrenceIds, nomisScheduledOutIds) { dpsId ->
    schedules.find { dpsId == it.dpsOccurrenceId }?.nomisEventId
  }

  private fun TemporaryAbsencesPrisonerMappingIdsDto.unexpectedNomisMovements(
    nomisMovementIds: List<OffenderTemporaryAbsenceId>,
    dpsMovementIds: List<UUID>,
  ) = findMissing(nomisMovementIds, dpsMovementIds) { nomisId ->
    this.movements.find { nomisId.bookingId == it.nomisBookingId && nomisId.sequence == it.nomisMovementSeq }?.dpsMovementId
  }

  private fun TemporaryAbsencesPrisonerMappingIdsDto.unexpectedDpsMovements(
    dpsMovementIds: List<UUID>,
    nomisMovementIds: List<OffenderTemporaryAbsenceId>,
  ) = findMissing(dpsMovementIds, nomisMovementIds) { dpsId ->
    movements.find { dpsId == it.dpsMovementId }?.let { OffenderTemporaryAbsenceId(it.nomisBookingId, it.nomisMovementSeq) }
  }

  private fun findMismatchedOccurrences(
    offenderNo: String,
    nomis: OffenderTemporaryAbsencesResponse,
    dps: PersonTapDetail,
    mappings: TemporaryAbsencesPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerTapDetails> {
    val nomisScheduleOutIds = nomis.bookings.flatMap { it.temporaryAbsenceApplications.flatMap { it.absences.mapNotNull { it.scheduledTemporaryAbsence?.eventId } } }
    val dpsOccurrenceIds = dps.scheduledAbsences.flatMap { it.occurrences.map { it.id } }

    val mismatches = mutableListOf<MismatchedPrisonerTapDetails>()
    mappings.matchingSchedulesOut(nomisScheduleOutIds, dpsOccurrenceIds)
      .forEach { (nomisEventId, dpsOccurrenceId) ->
        val (nomisScheduleOut, latestBooking) = nomis.findScheduleOut(nomisEventId)
        val dpsOccurrence = dps.findOccurrence(dpsOccurrenceId)
        fun mismatch(type: MismatchedPrisonerTapDetails.Type, nomisValue: String, dpsValue: String) = MismatchedPrisonerTapDetails(offenderNo, type, nomisEventId, dpsOccurrenceId, nomisValue, dpsValue)

        // status must match
        if (dpsOccurrence.statusCode.value.toNomisSchedulesStatus().first != nomisScheduleOut.eventStatus) {
          // check for exclusions
          when {
            // old booking not completed in NOMIS can be expired in DPS
            !latestBooking && nomisScheduleOut.eventStatus != "COMP" && dpsOccurrence.statusCode == ReconciliationOccurrence.StatusCode.EXPIRED -> {}
            // not excluded so we will publish the difference
            else -> mismatches.add(
              mismatch(MismatchedPrisonerTapDetails.Type.STATUS, nomisScheduleOut.eventStatus, dpsOccurrence.statusCode.value),
            )
          }
        }

        // reason code must match
        if (dpsOccurrence.reasonCode != nomisScheduleOut.eventSubType) {
          mismatches.add(mismatch(MismatchedPrisonerTapDetails.Type.REASON, nomisScheduleOut.eventSubType, dpsOccurrence.reasonCode))
        }

        // start time must match
        if (dpsOccurrence.start != nomisScheduleOut.startTime) {
          mismatches.add(mismatch(MismatchedPrisonerTapDetails.Type.START_TIME, "${nomisScheduleOut.startTime}", "${dpsOccurrence.start}"))
        }

        // end time must match
        if (dpsOccurrence.end != nomisScheduleOut.returnTime) {
          mismatches.add(mismatch(MismatchedPrisonerTapDetails.Type.END_TIME, "${nomisScheduleOut.returnTime}", "${dpsOccurrence.end}"))
        }

        // postcode must match
        if (dpsOccurrence.location?.postcode != nomisScheduleOut.toAddressPostcode) {
          mismatches.add(mismatch(MismatchedPrisonerTapDetails.Type.POSTCODE, "${nomisScheduleOut.toAddressPostcode}", "${dpsOccurrence.location?.postcode}"))
        }
      }

    return mismatches
  }

  // This finds each element of `sources` that exists in `targets` after transformation by `findTarget`
  private fun <SOURCE, TARGET> findMatches(
    sources: List<SOURCE>,
    targets: List<TARGET>,
    findTarget: (SOURCE) -> TARGET?,
  ): List<Pair<SOURCE, TARGET>> = sources.map { src -> src to findTarget(src) }
    .filter { (_, trg) -> trg in targets }
    .filter { (_, trg) -> trg != null }
    .map { (src, trg) -> src to trg!! }

  private fun TemporaryAbsencesPrisonerMappingIdsDto.matchingSchedulesOut(
    nomisScheduledOutIds: List<Long>,
    dpsOccurrenceIds: List<UUID>,
  ) = findMatches(nomisScheduledOutIds, dpsOccurrenceIds) { nomisId ->
    schedules.find { nomisId == it.nomisEventId }?.dpsOccurrenceId
  }

  private fun OffenderTemporaryAbsencesResponse.findScheduleOut(eventId: Long): Pair<ScheduledTemporaryAbsence, Boolean> = bookings.flatMap { booking ->
    booking.temporaryAbsenceApplications.flatMap { application ->
      application.absences.mapNotNull { absence ->
        if (absence.scheduledTemporaryAbsence?.eventId == eventId) absence.scheduledTemporaryAbsence to booking.latestBooking else null
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
    OCCURRENCES,
    SCHEDULED_OUT,
    SCHEDULED_IN,
    UNSCHEDULED_OUT,
    UNSCHEDULED_IN,
  }
}

class MismatchedPrisonerTapDetails(
  offenderNo: String,
  type: Type,
  val nomisEventId: Long,
  val dpsOccurrenceId: UUID,
  val nomisValue: String,
  val dpsValue: String,
) : MismatchPrisonerTaps(offenderNo, type.name) {
  enum class Type {
    STATUS,
    REASON,
    START_TIME,
    END_TIME,
    POSTCODE,
  }
}
