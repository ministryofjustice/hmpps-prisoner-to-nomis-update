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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
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
    checkPrisonerTapsMatch(prisonerIds.offenderNo, prisonerIds.bookingId).takeIf { it.isNotEmpty() }
      ?.let { prisonerIds }
  }.onFailure {
    log.error("Unable to match temporary absences for prisoner ${prisonerIds.offenderNo} booking ${prisonerIds.bookingId}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_TAPS-mismatch-error",
      mapOf(
        "offenderNo" to prisonerIds.offenderNo,
        "bookingId" to prisonerIds.bookingId,
        "reason" to (it.message ?: "unknown"),
      ),
    )
  }.getOrNull()

  suspend fun checkPrisonerTapsMatch(offenderNo: String, bookingId: Long): List<MismatchPrisonerTaps> = withContext(Dispatchers.Unconfined) {
    val nomisTaps = async { nomisApiService.getTemporaryAbsencesOrNull(offenderNo) }
    val dpsTaps = async { dpsApiService.getTapReconciliationDetail(offenderNo) }
    val mappings = async { mappingService.getTapMappingIds(offenderNo) }

    checkTapsMatch(
      offenderNo = offenderNo,
      bookingId = bookingId,
      dpsTaps = dpsTaps.await(),
      nomisTaps = nomisTaps.await(),
      mappings = mappings.await(),
    )
  }

  private fun checkTapsMatch(
    offenderNo: String,
    bookingId: Long,
    dpsTaps: PersonTapDetail,
    nomisTaps: OffenderTemporaryAbsencesResponse?,
    mappings: TemporaryAbsencesPrisonerMappingIdsDto,
  ): List<MismatchPrisonerTaps> {
    if (nomisTaps == null) {
      throw IllegalStateException("Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?")
    }

    val mismatchedCounts = findMismatchedCounts(nomisTaps, dpsTaps, mappings)
    mismatchedCounts.forEach {
      telemetryClient.trackEvent(
        "$TELEMETRY_ACTIVE_TAPS-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "bookingId" to bookingId,
          "type" to it.type.name,
          "nomisCount" to it.nomisCount.toString(),
          "dpsCount" to it.dpsCount.toString(),
          "unexpected-nomis-ids" to it.unexpectedNomisIds,
          "unexpected-dps-ids" to it.unexpectedDpsIds,
        ),
      )
    }

    // TODO we will also perform some occurrence detail comparisons and mismatch if different

    return mismatchedCounts.map { MismatchPrisonerTaps(offenderNo, bookingId, it.type) }
  }

  // Checks each NOMIS ID for a mapping to a real DPS ID, and vice versa. Any not found are returned
  private fun findMismatchedCounts(
    nomisIds: OffenderTemporaryAbsencesResponse,
    dpsIds: PersonTapDetail,
    mappings: TemporaryAbsencesPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerTapDetails> {
    val mismatchedDetails = mutableListOf<MismatchedPrisonerTapDetails>()

    val nomisApplicationIds = nomisIds.bookings.flatMap { it.temporaryAbsenceApplications.map { it.movementApplicationId } }
    val dpsAuthorisationIds = dpsIds.scheduledAbsences.map { it.id }
    if (nomisApplicationIds.size != dpsAuthorisationIds.size) {
      mismatchedDetails.add(
        MismatchedPrisonerTapDetails(
          type = MismatchedPrisonerTapDetails.Types.AUTHORISATIONS,
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
        MismatchedPrisonerTapDetails(
          type = MismatchedPrisonerTapDetails.Types.OCCURRENCES,
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
        MismatchedPrisonerTapDetails(
          type = MismatchedPrisonerTapDetails.Types.SCHEDULED_OUT,
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
        MismatchedPrisonerTapDetails(
          type = MismatchedPrisonerTapDetails.Types.SCHEDULED_IN,
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
        MismatchedPrisonerTapDetails(
          type = MismatchedPrisonerTapDetails.Types.UNSCHEDULED_OUT,
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
        MismatchedPrisonerTapDetails(
          type = MismatchedPrisonerTapDetails.Types.UNSCHEDULED_IN,
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
}

data class MismatchPrisonerTaps(
  val offenderNo: String,
  val bookingId: Long,
  val type: MismatchedPrisonerTapDetails.Types,
)

data class MismatchedPrisonerTapDetails(
  val type: Types,
  val nomisCount: Int,
  val dpsCount: Int,
  val unexpectedNomisIds: String,
  val unexpectedDpsIds: String,
) {
  enum class Types {
    AUTHORISATIONS,
    OCCURRENCES,
    SCHEDULED_OUT,
    SCHEDULED_IN,
    UNSCHEDULED_OUT,
    UNSCHEDULED_IN,
  }
}
