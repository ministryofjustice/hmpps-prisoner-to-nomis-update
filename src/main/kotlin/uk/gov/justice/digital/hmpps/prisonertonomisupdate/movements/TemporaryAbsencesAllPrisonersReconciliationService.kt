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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapCounts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.ReconciliationMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.util.UUID

@Service
class TemporaryAbsencesAllPrisonersReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: ExternalMovementsNomisApiService,
  private val dpsApiService: ExternalMovementsDpsApiService,
  private val nomisPrisonerApiService: NomisApiService,
  private val mappingService: ExternalMovementsMappingApiService,
  @param:Value($$"${reports.temporary-absences.all-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.temporary-absences.all-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_ALL_TAPS = "temporary-absences-all-reconciliation"
  }

  suspend fun generateTapAllPrisonersReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_TAPS-requested",
      mapOf(),
    )

    runCatching { generateTapAllPrisonersReconciliationReport() }
      .onSuccess {
        log.info("Temporary absences all prisoners reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_TAPS-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_ALL_TAPS-report", mapOf("success" to "false"))
        log.error("Temporary absences all prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerId>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateTapAllPrisonersReconciliationReport(): ReconciliationResult<PrisonerId> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonerTapsMatch,
    nextPage = ::getNextPrisonersForPage,
  )

  private suspend fun getNextPrisonersForPage(lastOffenderId: Long): ReconciliationPageResult<PrisonerId> = runCatching {
    nomisPrisonerApiService.getAllPrisoners(fromId = lastOffenderId, pageSize = pageSize)
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_TAPS-mismatch-page-error",
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

  suspend fun checkPrisonerTapsMatch(prisonerId: PrisonerId): PrisonerId? = runCatching {
    checkPrisonerTapsMatch(prisonerId.offenderNo).takeIf { it.isNotEmpty() }?.let { prisonerId }
  }.onFailure {
    log.error("Unable to match temporary absences for prisoner with ${prisonerId.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_TAPS-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
      ),
    )
  }.getOrNull()

  suspend fun checkPrisonerTapsMatch(offenderNo: String): List<MismatchPrisonerTaps> {
    val (nomisTaps, dpsTaps) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getTemporaryAbsenceSummary(offenderNo) } to
        async { dpsApiService.getTapReconciliation(offenderNo) }
    }.awaitBoth()
    return checkTapsMatch(
      offenderNo = offenderNo,
      dpsTaps = dpsTaps,
      nomisTaps = nomisTaps,
    )
  }

  private suspend fun checkTapsMatch(offenderNo: String, dpsTaps: PersonTapCounts, nomisTaps: OffenderTemporaryAbsenceSummaryResponse): List<MismatchPrisonerTaps> {
    val mismatches = mutableListOf<MismatchPrisonerTaps>()
    if (dpsTaps.authorisations.count != nomisTaps.applications.count.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.AUTHORISATIONS, dpsTaps.authorisations.count, nomisTaps.applications.count.toInt())
    }
    if (dpsTaps.occurrences.count != nomisTaps.scheduledOutMovements.count.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.OCCURRENCES, dpsTaps.occurrences.count, nomisTaps.scheduledOutMovements.count.toInt())
    }
    if (dpsTaps.movements.scheduled.outCount != nomisTaps.movements.scheduled.outCount.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.SCHEDULED_OUT, dpsTaps.movements.scheduled.outCount, nomisTaps.movements.scheduled.outCount.toInt())
    }
    if (dpsTaps.movements.scheduled.inCount != nomisTaps.movements.scheduled.inCount.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.SCHEDULED_IN, dpsTaps.movements.scheduled.inCount, nomisTaps.movements.scheduled.inCount.toInt())
    }
    if (dpsTaps.movements.unscheduled.outCount != nomisTaps.movements.unscheduled.outCount.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.UNSCHEDULED_OUT, dpsTaps.movements.unscheduled.outCount, nomisTaps.movements.unscheduled.outCount.toInt())
    }
    if (dpsTaps.movements.unscheduled.inCount != nomisTaps.movements.unscheduled.inCount.toInt()) {
      mismatches += MismatchPrisonerTaps(offenderNo, TapMismatchTypes.UNSCHEDULED_IN, dpsTaps.movements.unscheduled.inCount, nomisTaps.movements.unscheduled.inCount.toInt())
    }

    if (mismatches.isEmpty()) return emptyList()

    withContext(Dispatchers.Unconfined) {
      val nomisIds = async { nomisApiService.getTemporaryAbsenceIds(offenderNo) }
      val dpsIds = async { dpsApiService.getTapReconciliationDetail(offenderNo) }
      val mappings = async { mappingService.getTapMappingIds(offenderNo) }

      mismatches.forEach {
        val (unexpectedNomisIds, unexpectedDpsIds) = findMismatchedIds(it.type, nomisIds.await(), dpsIds.await(), mappings.await())
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_TAPS-mismatch",
          mapOf(
            "offenderNo" to offenderNo,
            "type" to it.type.name,
            "nomisCount" to it.nomisCount.toString(),
            "dpsCount" to it.dpsCount.toString(),
            "unexpected-nomis-ids" to unexpectedNomisIds,
            "unexpected-dps-ids" to unexpectedDpsIds,
          ),
        )
      }
    }

    return mismatches.toList()
  }

  private data class MismatchedIds(val nomisIds: String, val dpsIds: String)

  // Checks each NOMIS ID for a mapping to a real DPS ID, and vice versa. Any not found are returned
  private fun findMismatchedIds(
    type: TapMismatchTypes,
    nomisIds: OffenderTemporaryAbsenceIdsResponse,
    dpsIds: PersonTapDetail,
    mappings: TemporaryAbsencesPrisonerMappingIdsDto,
  ): MismatchedIds = when (type) {
    TapMismatchTypes.AUTHORISATIONS -> {
      val dpsAuthorisationIds = dpsIds.scheduledAbsences.map { it.id }
      mappings.unexpectedApplications(nomisIds.applicationIds, dpsAuthorisationIds) to
        mappings.unexpectedAuthorisations(dpsAuthorisationIds, nomisIds.applicationIds)
    }

    TapMismatchTypes.OCCURRENCES -> {
      val dpsOccurrenceIds = dpsIds.scheduledAbsences.flatMap { it.occurrences.map { it.id } }
      mappings.unexpectedScheduledOut(nomisIds.scheduleOutIds, dpsOccurrenceIds) to
        mappings.unexpectedOccurrences(dpsOccurrenceIds, nomisIds.scheduleOutIds)
    }

    TapMismatchTypes.SCHEDULED_OUT -> {
      val dpsScheduledOutIds = dpsIds.scheduledAbsences.flatMap { it.occurrences.flatMap { it.movements.filter { it.direction == ReconciliationMovement.Direction.OUT }.map { it.id } } }
      mappings.unexpectedNomisMovements(nomisIds.scheduledMovementOutIds, dpsScheduledOutIds).format() to
        mappings.unexpectedDpsMovements(dpsScheduledOutIds, nomisIds.scheduledMovementOutIds)
    }

    TapMismatchTypes.SCHEDULED_IN -> {
      val dpsScheduledInIds = dpsIds.scheduledAbsences.flatMap { it.occurrences.flatMap { it.movements.filter { it.direction == ReconciliationMovement.Direction.IN }.map { it.id } } }
      mappings.unexpectedNomisMovements(nomisIds.scheduledMovementInIds, dpsScheduledInIds).format() to
        mappings.unexpectedDpsMovements(dpsScheduledInIds, nomisIds.scheduledMovementInIds)
    }

    TapMismatchTypes.UNSCHEDULED_OUT -> {
      val dpsUnscheduledOutIds = dpsIds.unscheduledMovements.filter { it.direction == ReconciliationMovement.Direction.OUT }.map { it.id }
      mappings.unexpectedNomisMovements(nomisIds.unscheduledMovementOutIds, dpsUnscheduledOutIds).format() to
        mappings.unexpectedDpsMovements(dpsUnscheduledOutIds, nomisIds.unscheduledMovementOutIds)
    }

    TapMismatchTypes.UNSCHEDULED_IN -> {
      val dpsUnscheduledInIds = dpsIds.unscheduledMovements.filter { it.direction == ReconciliationMovement.Direction.IN }.map { it.id }
      mappings.unexpectedNomisMovements(nomisIds.unscheduledMovementInIds, dpsUnscheduledInIds).format() to
        mappings.unexpectedDpsMovements(dpsUnscheduledInIds, nomisIds.unscheduledMovementInIds)
    }
  }.let { MismatchedIds(nomisIds = "${it.first}", dpsIds = "${it.second}") }

  private fun List<OffenderTemporaryAbsenceId>.format() = joinToString(prefix = "[", postfix = "]") { "${it.bookingId}_${it.sequence}" }

  // This checks each element of `sources` exists in `targets` after transformation by `findMapping`
  private fun <SOURCE, TARGET> findMissing(
    sources: List<SOURCE>,
    targets: List<TARGET>,
    findMapping: (SOURCE) -> TARGET?,
  ) = sources.map { src -> src to findMapping(src) }
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
  val type: TapMismatchTypes,
  val dpsCount: Int,
  val nomisCount: Int,
)

enum class TapMismatchTypes {
  AUTHORISATIONS,
  OCCURRENCES,
  SCHEDULED_OUT,
  SCHEDULED_IN,
  UNSCHEDULED_OUT,
  UNSCHEDULED_IN,
}
