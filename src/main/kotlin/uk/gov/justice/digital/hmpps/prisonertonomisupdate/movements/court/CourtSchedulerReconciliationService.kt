package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.ReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingMappingService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchPrisonerCourtMovementDetails.Type.MOVEMENT_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchPrisonerCourtMovementDetails.Type.REASON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchPrisonerCourtScheduleDetails.Type.COURT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchPrisonerCourtScheduleDetails.Type.EVENT_STATUS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchPrisonerCourtScheduleDetails.Type.EVENT_TYPE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchPrisonerCourtScheduleDetails.Type.PRISON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchPrisonerCourtScheduleDetails.Type.START_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.MismatchedPrisonerCourtMovementIds.Type.MISSING_MAPPING_SCHEDULE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.findMatches
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.findMissing
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapActivePrisonersReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapActivePrisonersReconciliationService.Companion.TELEMETRY_ACTIVE_TAPS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapAllPrisonersReconciliationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtMovements
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderCourtMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime
import java.util.*

@Service
class CourtSchedulerReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: CourtSchedulerNomisApiService,
  private val dpsApiService: CourtSchedulerDpsApiService,
  private val nomisPrisonerApiService: NomisApiService,
  private val mappingService: CourtSchedulerMappingApiService,
  private val courtSentencingMappingService: CourtSentencingMappingService,
  @param:Value($$"${reports.temporary-absences.active-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.temporary-absences.active-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_COURT_SCHEDULER = "court-scheduler-reconciliation"
  }

  suspend fun generateCourtSchedulerReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ACTIVE_TAPS-requested",
      mapOf(),
    )

    runCatching { generateCourtSchedulerReconciliationReport() }
      .onSuccess {
        log.info("Court scheduler reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_COURT_SCHEDULER-report",
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
        TapActivePrisonersReconciliationService.log.error("Temporary absences all prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerId>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateCourtSchedulerReconciliationReport(): ReconciliationResult<PrisonerId> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonersMatch,
    nextPage = ::getNextPrisonersForPage,
  )

  private suspend fun getNextPrisonersForPage(lastOffenderId: Long): ReconciliationPageResult<PrisonerId> = runCatching {
    nomisPrisonerApiService.getAllPrisoners(fromId = lastOffenderId, pageSize = pageSize)
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_SCHEDULER-mismatch-page-error",
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
    .also { TapAllPrisonersReconciliationService.log.info("Page requested from offender ID: $lastOffenderId, with $pageSize offenders") }

  suspend fun checkPrisonersMatch(prisonerId: PrisonerId): PrisonerId? = runCatching {
    checkPrisonersMatch(prisonerId.offenderNo).takeIf { it.isNotEmpty() }
      ?.let { prisonerId }
  }.onFailure {
    TapActivePrisonersReconciliationService.log.error("Unable to match temporary absences for prisoner ${prisonerId.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_SCHEDULER-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
    )
  }.getOrNull()

  suspend fun checkPrisonersMatch(offenderNo: String, suppressTelemetry: Boolean = false): List<MismatchedPrisonerCourtMovements> = withContext(Dispatchers.Unconfined) {
    val nomisMovements = async { nomisApiService.getOffenderCourtMovementsOrNull(offenderNo) }
    val dpsMovements = async { dpsApiService.getCourtSchedulerReconciliation(offenderNo) }
    val movementMappings = async { mappingService.getCourtSchedulerPrisonMappingIds(offenderNo) }

    checkPrisonersMatch(offenderNo, nomisMovements.await(), dpsMovements.await(), movementMappings.await(), suppressTelemetry)
  }

  private suspend fun checkPrisonersMatch(
    offenderNo: String,
    nomisMovements: OffenderCourtMovementsResponse?,
    dpsMovements: ReconciliationResponse,
    movementMappings: CourtSchedulerPrisonerMappingIdsDto,
    suppressTelemetry: Boolean,
  ): List<MismatchedPrisonerCourtMovements> {
    if (nomisMovements == null) {
      throw IllegalStateException("Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?")
    }

    val mismatchedEntities = findMismatchedEntities(offenderNo, nomisMovements, dpsMovements, movementMappings)
    if (!suppressTelemetry) {
      mismatchedEntities.forEach {
        telemetryClient.trackEvent(
          "$TELEMETRY_COURT_SCHEDULER-mismatch",
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

    val mismatchedScheduleDetails = findMismatchedCourtScheduleDetails(offenderNo, nomisMovements, dpsMovements, movementMappings)
    if (!suppressTelemetry) {
      mismatchedScheduleDetails.forEach {
        telemetryClient.trackEvent(
          "$TELEMETRY_COURT_SCHEDULER-mismatch",
          mapOf(
            "offenderNo" to offenderNo,
            "type" to it.type,
            "nomisEventId" to it.nomisEventId,
            "dpsCourtEventId" to it.dpsCourtEventId,
          ),
        )
      }
    }

    val mismatchedMovementDetails = findMismatchedScheduledMovementOutDetails(offenderNo, nomisMovements, dpsMovements, movementMappings) +
      findMismatchedScheduledMovementInDetails(offenderNo, nomisMovements, dpsMovements, movementMappings) +
      findMismatchedUnscheduledMovementOutDetails(offenderNo, nomisMovements, dpsMovements, movementMappings) +
      findMismatchedUnscheduledMovementInDetails(offenderNo, nomisMovements, dpsMovements, movementMappings)

    if (!suppressTelemetry) {
      mismatchedMovementDetails.forEach {
        telemetryClient.trackEvent(
          "$TELEMETRY_COURT_SCHEDULER-mismatch",
          mapOf(
            "offenderNo" to offenderNo,
            "type" to it.type,
            "nomisMovementId" to it.nomisMovementId,
            "dpsMovementId" to it.dpsCourtMovementId,
          ),
        )
      }
    }

    return mismatchedEntities + mismatchedScheduleDetails + mismatchedMovementDetails
  }

  private fun findMismatchedEntities(
    offenderNo: String,
    nomisMovements: OffenderCourtMovementsResponse,
    dpsMovements: ReconciliationResponse,
    movementMappings: CourtSchedulerPrisonerMappingIdsDto,
  ): List<MismatchedPrisonerCourtMovementIds> {
    val mismatches = mutableListOf<MismatchedPrisonerCourtMovementIds>()

    // Check for scheduled court events existing only in 1 system
    val nomisCourtEventIds = nomisMovements.bookings.flatMap { it.courtSchedules.map { it.eventId } }
    val dpsCourtEventIds = dpsMovements.courtEvents.map { it.courtEvent.dpsId!! }
    if (nomisCourtEventIds.size != dpsCourtEventIds.size) {
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.SCHEDULE,
          nomisCount = nomisCourtEventIds.size,
          dpsCount = dpsCourtEventIds.size,
          unexpectedNomisIds = movementMappings.unexpectedNomisCourtEvents(nomisCourtEventIds, dpsCourtEventIds).toString(),
          unexpectedDpsIds = movementMappings.unexpectedDpsCourtEvents(dpsCourtEventIds, nomisCourtEventIds).toString(),
        ),
      )
    }

    // Check for scheduled court movements OUT existing only in 1 system
    val nomisScheduledMovementOutIds = nomisMovements.bookings.flatMap { booking -> booking.courtSchedules.mapNotNull { it.courtMovementOut?.let { MovementId(booking.bookingId, it.sequence) } } }
    val dpsScheduledMovementOutIds = dpsMovements.courtEvents.flatMap { it.movements.filter { it.directionCode == "OUT" }.map { it.dpsId!! } }
    if (nomisScheduledMovementOutIds.size != dpsScheduledMovementOutIds.size) {
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.SCHEDULED_MOVEMENT_OUT,
          nomisCount = nomisScheduledMovementOutIds.size,
          dpsCount = dpsScheduledMovementOutIds.size,
          unexpectedNomisIds = movementMappings.unexpectedNomisMovements(nomisScheduledMovementOutIds, dpsScheduledMovementOutIds).toString(),
          unexpectedDpsIds = movementMappings.unexpectedDpsMovements(dpsScheduledMovementOutIds, nomisScheduledMovementOutIds).toString(),
        ),
      )
    }

    // Check for scheduled court movements IN existing only in 1 system
    val nomisScheduledMovementInIds = nomisMovements.bookings.flatMap { booking -> booking.courtSchedules.mapNotNull { it.courtMovementIn?.let { MovementId(booking.bookingId, it.sequence) } } }
    val dpsScheduledMovementInIds = dpsMovements.courtEvents.flatMap { it.movements.filter { it.directionCode == "IN" }.map { it.dpsId!! } }
    if (nomisScheduledMovementInIds.size != dpsScheduledMovementInIds.size) {
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.SCHEDULED_MOVEMENT_IN,
          nomisCount = nomisScheduledMovementInIds.size,
          dpsCount = dpsScheduledMovementInIds.size,
          unexpectedNomisIds = movementMappings.unexpectedNomisMovements(nomisScheduledMovementInIds, dpsScheduledMovementInIds).toString(),
          unexpectedDpsIds = movementMappings.unexpectedDpsMovements(dpsScheduledMovementInIds, nomisScheduledMovementInIds).toString(),
        ),
      )
    }

    // Check for unscheduled court movements OUT existing only in 1 system
    val nomisUnscheduledMovementOutIds = nomisMovements.bookings.flatMap { booking -> booking.unscheduledCourtMovementOuts.map { MovementId(booking.bookingId, it.sequence) } }
    val dpsUnscheduledMovementOutIds = dpsMovements.unscheduledMovements.filter { it.directionCode == "OUT" }.map { it.dpsId!! }
    if (nomisUnscheduledMovementOutIds.size != dpsUnscheduledMovementOutIds.size) {
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.UNSCHEDULED_MOVEMENT_OUT,
          nomisCount = nomisUnscheduledMovementOutIds.size,
          dpsCount = dpsUnscheduledMovementOutIds.size,
          unexpectedNomisIds = movementMappings.unexpectedNomisMovements(nomisUnscheduledMovementOutIds, dpsUnscheduledMovementOutIds).toString(),
          unexpectedDpsIds = movementMappings.unexpectedDpsMovements(dpsUnscheduledMovementOutIds, nomisUnscheduledMovementOutIds).toString(),
        ),
      )
    }

    // Check for unscheduled court movements IN existing only in 1 system
    val nomisUnscheduledMovementInIds = nomisMovements.bookings.flatMap { booking -> booking.unscheduledCourtMovementIns.map { MovementId(booking.bookingId, it.sequence) } }
    val dpsUnscheduledMovementInIds = dpsMovements.unscheduledMovements.filter { it.directionCode == "IN" }.map { it.dpsId!! }
    if (nomisUnscheduledMovementInIds.size != dpsUnscheduledMovementInIds.size) {
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.UNSCHEDULED_MOVEMENT_IN,
          nomisCount = nomisUnscheduledMovementInIds.size,
          dpsCount = dpsUnscheduledMovementInIds.size,
          unexpectedNomisIds = movementMappings.unexpectedNomisMovements(nomisUnscheduledMovementInIds, dpsUnscheduledMovementInIds).toString(),
          unexpectedDpsIds = movementMappings.unexpectedDpsMovements(dpsUnscheduledMovementInIds, nomisUnscheduledMovementInIds).toString(),
        ),
      )
    }

    // check for missing schedule mappings
    val matchedScheduleIds = movementMappings.matchingSchedules(nomisCourtEventIds, dpsCourtEventIds)
    if (matchedScheduleIds.size != nomisCourtEventIds.size || matchedScheduleIds.size != dpsCourtEventIds.size) {
      val missingNomisIds = nomisCourtEventIds - matchedScheduleIds.map { it.first }.toSet()
      val missingDpsIds = dpsCourtEventIds - matchedScheduleIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MISSING_MAPPING_SCHEDULE,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    // check for missing scheduled movement out mappings
    val matchedScheduledMovementOutIds = movementMappings.matchingMovements(nomisScheduledMovementOutIds, dpsScheduledMovementOutIds)
    if (matchedScheduledMovementOutIds.size != nomisScheduledMovementOutIds.size || matchedScheduledMovementOutIds.size != dpsScheduledMovementOutIds.size) {
      val missingNomisIds = nomisScheduledMovementOutIds - matchedScheduledMovementOutIds.map { it.first }.toSet()
      val missingDpsIds = dpsScheduledMovementOutIds - matchedScheduledMovementOutIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.MISSING_MAPPING_SCHEDULED_MOVEMENT_OUT,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    // check for missing scheduled movement in mappings
    val matchedScheduledMovementInIds = movementMappings.matchingMovements(nomisScheduledMovementInIds, dpsScheduledMovementInIds)
    if (matchedScheduledMovementInIds.size != nomisScheduledMovementInIds.size || matchedScheduledMovementInIds.size != dpsScheduledMovementInIds.size) {
      val missingNomisIds = nomisScheduledMovementInIds - matchedScheduledMovementInIds.map { it.first }.toSet()
      val missingDpsIds = dpsScheduledMovementInIds - matchedScheduledMovementInIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.MISSING_MAPPING_SCHEDULED_MOVEMENT_IN,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    // check for missing unscheduled movement out mappings
    val matchedUnscheduledMovementOutIds = movementMappings.matchingMovements(nomisUnscheduledMovementOutIds, dpsUnscheduledMovementOutIds)
    if (matchedUnscheduledMovementOutIds.size != nomisUnscheduledMovementOutIds.size || matchedUnscheduledMovementOutIds.size != dpsUnscheduledMovementOutIds.size) {
      val missingNomisIds = nomisUnscheduledMovementOutIds - matchedUnscheduledMovementOutIds.map { it.first }.toSet()
      val missingDpsIds = dpsUnscheduledMovementOutIds - matchedUnscheduledMovementOutIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.MISSING_MAPPING_UNSCHEDULED_MOVEMENT_OUT,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    // check for missing unscheduled movement in mappings
    val matchedUnscheduledMovementInIds = movementMappings.matchingMovements(nomisUnscheduledMovementInIds, dpsUnscheduledMovementInIds)
    if (matchedUnscheduledMovementInIds.size != nomisUnscheduledMovementInIds.size || matchedUnscheduledMovementInIds.size != dpsUnscheduledMovementInIds.size) {
      val missingNomisIds = nomisUnscheduledMovementInIds - matchedUnscheduledMovementInIds.map { it.first }.toSet()
      val missingDpsIds = dpsUnscheduledMovementInIds - matchedUnscheduledMovementInIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerCourtMovementIds(
          offenderNo = offenderNo,
          type = MismatchedPrisonerCourtMovementIds.Type.MISSING_MAPPING_UNSCHEDULED_MOVEMENT_IN,
          nomisCount = missingNomisIds.size,
          dpsCount = missingDpsIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    return mismatches.toList()
  }

  private suspend fun findMismatchedCourtScheduleDetails(
    offenderNo: String,
    nomis: OffenderCourtMovementsResponse,
    dps: ReconciliationResponse,
    mappings: CourtSchedulerPrisonerMappingIdsDto,
  ): List<MismatchPrisonerCourtScheduleDetails> {
    val nomisCourtEventIds = nomis.bookings.flatMap { it.courtSchedules.map { it.eventId } }
    val dpsCourtAppearanceIds = dps.courtEvents.map { it.courtEvent.dpsId!! }
    val dpsCourtSentencingAppearanceIds = nomis.bookings.findCourtSentencingIds()

    val mismatches = mutableListOf<MismatchPrisonerCourtScheduleDetails>()
    mappings.matchingSchedules(nomisCourtEventIds, dpsCourtAppearanceIds).forEach { (nomisId, dpsId) ->
      val nomisCourtEvent = nomis.findNomisCourtSchedule(nomisId)
      val dpsCourtEvent = dps.findDpsCourtSchedule(dpsId)
      fun mismatch(type: MismatchPrisonerCourtScheduleDetails.Type, nomisValue: String, dpsValue: String) = MismatchPrisonerCourtScheduleDetails(offenderNo, type, nomisId, dpsId, nomisValue, dpsValue)

      // prison must match
      if (nomisCourtEvent.prison != dpsCourtEvent.courtEvent.prisonCodeAtTimeOfScheduling) {
        mismatches.add(mismatch(PRISON, nomisCourtEvent.prison, dpsCourtEvent.courtEvent.prisonCodeAtTimeOfScheduling))
      }

      // court must match
      if (nomisCourtEvent.court != dpsCourtEvent.courtEvent.agyLocId) {
        mismatches.add(mismatch(COURT, nomisCourtEvent.court, dpsCourtEvent.courtEvent.agyLocId))
      }

      // start time must match
      if (nomisCourtEvent.startTime != dpsCourtEvent.courtEvent.startDateTime) {
        mismatches.add(mismatch(START_TIME, "${nomisCourtEvent.startTime}", "${dpsCourtEvent.courtEvent.startDateTime}"))
      }

      // event type must match
      if (nomisCourtEvent.eventType != dpsCourtEvent.courtEvent.courtEventType) {
        mismatches.add(mismatch(EVENT_TYPE, nomisCourtEvent.eventType, dpsCourtEvent.courtEvent.courtEventType))
      }

      // event status must match
      if (nomisCourtEvent.eventStatus != dpsCourtEvent.courtEvent.eventStatus) {
        mismatches.add(mismatch(EVENT_STATUS, nomisCourtEvent.eventStatus, dpsCourtEvent.courtEvent.eventStatus))
      }

      // external reference must match
      if (nomisCourtEvent.courtCaseId != null) {
        val expectedDpsCourtAppearanceId = dpsCourtSentencingAppearanceIds[nomisCourtEvent.courtCaseId]
        val actualDpsCourtAppearanceId = dpsCourtEvent.courtEvent.externalReferenceUrn
        // The external reference is not the exact court sentencing UUID, but it does contain the UUID
        if (expectedDpsCourtAppearanceId == null || actualDpsCourtAppearanceId == null || !actualDpsCourtAppearanceId.contains(expectedDpsCourtAppearanceId)) {
          mismatches.add(mismatch(MismatchPrisonerCourtScheduleDetails.Type.EXTERNAL_REFERENCE_URN, expectedDpsCourtAppearanceId ?: "null", dpsCourtEvent.courtEvent.externalReferenceUrn ?: "null"))
        }
      }
    }

    return mismatches.toList()
  }

  private fun findMismatchedScheduledMovementOutDetails(
    offenderNo: String,
    nomis: OffenderCourtMovementsResponse,
    dps: ReconciliationResponse,
    mappings: CourtSchedulerPrisonerMappingIdsDto,
  ): List<MismatchPrisonerCourtMovementDetails> {
    val nomisScheduledMovementOutIds = nomis.bookings.flatMap { booking ->
      booking.courtSchedules.mapNotNull {
        it.courtMovementOut?.let { MovementId(booking.bookingId, it.sequence) }
      }
    }
    val dpsScheduledMovementOutIds = dps.courtEvents.flatMap { it.movements.filter { it.directionCode == "OUT" }.map { it.dpsId!! } }

    val mismatches = mutableListOf<MismatchPrisonerCourtMovementDetails>()
    mappings.matchingMovements(nomisScheduledMovementOutIds, dpsScheduledMovementOutIds).forEach { (nomisId, dpsId) ->
      val nomisMovement = nomis.findNomisScheduledMovementOut(nomisId)
      val dpsMovement = dps.findDpsCourtMovementOut(dpsId)

      mismatches.addAll(
        compareMovements(
          offenderNo, nomisId, dpsId,
          nomisMovement.fromPrison, dpsMovement.fromAgencyId,
          nomisMovement.movementTime, dpsMovement.movementTime,
          nomisMovement.movementReason, dpsMovement.movementReasonCode,
          nomisMovement.toCourt, dpsMovement.toAgencyId,
        ),
      )
    }

    return mismatches.toList()
  }

  private fun findMismatchedScheduledMovementInDetails(
    offenderNo: String,
    nomis: OffenderCourtMovementsResponse,
    dps: ReconciliationResponse,
    mappings: CourtSchedulerPrisonerMappingIdsDto,
  ): List<MismatchPrisonerCourtMovementDetails> {
    val nomisScheduledMovementInIds = nomis.bookings.flatMap { booking ->
      booking.courtSchedules.mapNotNull {
        it.courtMovementIn?.let { MovementId(booking.bookingId, it.sequence) }
      }
    }
    val dpsScheduledMovementInIds = dps.courtEvents.flatMap { it.movements.filter { it.directionCode == "IN" }.map { it.dpsId!! } }

    val mismatches = mutableListOf<MismatchPrisonerCourtMovementDetails>()
    mappings.matchingMovements(nomisScheduledMovementInIds, dpsScheduledMovementInIds).forEach { (nomisId, dpsId) ->
      val nomisMovement = nomis.findNomisScheduledMovementIn(nomisId)
      val dpsMovement = dps.findDpsCourtMovementIn(dpsId)

      mismatches.addAll(
        compareMovements(
          offenderNo, nomisId, dpsId,
          nomisMovement.toPrison, dpsMovement.toAgencyId,
          nomisMovement.movementTime, dpsMovement.movementTime,
          nomisMovement.movementReason, dpsMovement.movementReasonCode,
          nomisMovement.fromCourt, dpsMovement.fromAgencyId,
        ),
      )
    }

    return mismatches.toList()
  }

  private fun findMismatchedUnscheduledMovementOutDetails(
    offenderNo: String,
    nomis: OffenderCourtMovementsResponse,
    dps: ReconciliationResponse,
    mappings: CourtSchedulerPrisonerMappingIdsDto,
  ): List<MismatchPrisonerCourtMovementDetails> {
    val nomisUnscheduledMovementOutIds = nomis.bookings.flatMap { booking ->
      booking.unscheduledCourtMovementOuts.map { MovementId(booking.bookingId, it.sequence) }
    }
    val dpsUnscheduledMovementOutIds = dps.unscheduledMovements.filter { it.directionCode == "OUT" }.map { it.dpsId!! }

    val mismatches = mutableListOf<MismatchPrisonerCourtMovementDetails>()
    mappings.matchingMovements(nomisUnscheduledMovementOutIds, dpsUnscheduledMovementOutIds).forEach { (nomisId, dpsId) ->
      val nomisMovement = nomis.findNomisUnscheduledMovementOut(nomisId)
      val dpsMovement = dps.findDpsUnscheduledMovementOut(dpsId)

      mismatches.addAll(
        compareMovements(
          offenderNo, nomisId, dpsId,
          nomisMovement.fromPrison, dpsMovement.fromAgencyId,
          nomisMovement.movementTime, dpsMovement.movementTime,
          nomisMovement.movementReason, dpsMovement.movementReasonCode,
          nomisMovement.toCourt, dpsMovement.toAgencyId,
        ),
      )
    }

    return mismatches.toList()
  }

  private fun findMismatchedUnscheduledMovementInDetails(
    offenderNo: String,
    nomis: OffenderCourtMovementsResponse,
    dps: ReconciliationResponse,
    mappings: CourtSchedulerPrisonerMappingIdsDto,
  ): List<MismatchPrisonerCourtMovementDetails> {
    val nomisUnscheduledMovementInIds = nomis.bookings.flatMap { booking ->
      booking.unscheduledCourtMovementIns.map { MovementId(booking.bookingId, it.sequence) }
    }
    val dpsUnscheduledMovementInIds = dps.unscheduledMovements.filter { it.directionCode == "IN" }.map { it.dpsId!! }

    val mismatches = mutableListOf<MismatchPrisonerCourtMovementDetails>()
    mappings.matchingMovements(nomisUnscheduledMovementInIds, dpsUnscheduledMovementInIds).forEach { (nomisId, dpsId) ->
      val nomisMovement = nomis.findNomisUnscheduledMovementIn(nomisId)
      val dpsMovement = dps.findDpsUnscheduledMovementIn(dpsId)

      mismatches.addAll(
        compareMovements(
          offenderNo, nomisId, dpsId,
          nomisMovement.toPrison, dpsMovement.toAgencyId,
          nomisMovement.movementTime, dpsMovement.movementTime,
          nomisMovement.movementReason, dpsMovement.movementReasonCode,
          nomisMovement.fromCourt, dpsMovement.fromAgencyId,
        ),
      )
    }

    return mismatches.toList()
  }

  private fun compareMovements(
    offenderNo: String,
    nomisId: MovementId,
    dpsId: UUID,
    nomisPrison: String,
    dpsPrison: String,
    nomisTime: LocalDateTime,
    dpsTime: String,
    nomisReason: String,
    dpsReason: String,
    nomisCourt: String?,
    dpsCourt: String,
  ): List<MismatchPrisonerCourtMovementDetails> {
    val mismatches = mutableListOf<MismatchPrisonerCourtMovementDetails>()
    fun mismatch(type: MismatchPrisonerCourtMovementDetails.Type, nomisValue: String, dpsValue: String) = MismatchPrisonerCourtMovementDetails(offenderNo, type, nomisId, dpsId, nomisValue, dpsValue)

    // prison must match
    if (nomisPrison != dpsPrison) {
      mismatches.add(mismatch(MismatchPrisonerCourtMovementDetails.Type.PRISON, nomisPrison, dpsPrison))
    }

    // movement time must match
    if (nomisTime != LocalDateTime.parse(dpsTime)) {
      mismatches.add(mismatch(MOVEMENT_TIME, "$nomisTime", dpsTime))
    }

    // movement reason must match
    if (nomisReason != dpsReason) {
      mismatches.add(mismatch(REASON, nomisReason, dpsReason))
    }

    // court must match
    if (nomisCourt != dpsCourt) {
      mismatches.add(mismatch(MismatchPrisonerCourtMovementDetails.Type.COURT, nomisCourt ?: "null", dpsCourt))
    }

    return mismatches.toList()
  }

  private fun CourtSchedulerPrisonerMappingIdsDto.unexpectedNomisCourtEvents(
    nomisCourtEventIds: List<Long>,
    dpsCourtEventIds: List<UUID>,
  ) = findMissing(nomisCourtEventIds, dpsCourtEventIds) { eventId ->
    schedules.find { it.nomisEventId == eventId }?.dpsCourtAppearanceId
  }

  private fun CourtSchedulerPrisonerMappingIdsDto.unexpectedDpsCourtEvents(
    dpsCourtEventIds: List<UUID>,
    nomisCourtEventIds: List<Long>,
  ) = findMissing(dpsCourtEventIds, nomisCourtEventIds) { courtAppearanceId ->
    schedules.find { it.dpsCourtAppearanceId == courtAppearanceId }?.nomisEventId
  }

  private fun CourtSchedulerPrisonerMappingIdsDto.unexpectedNomisMovements(
    nomisMovementIds: List<MovementId>,
    dpsMovementIds: List<UUID>,
  ) = findMissing(nomisMovementIds, dpsMovementIds) { nomisMovementId ->
    movements.find { it.nomisBookingId == nomisMovementId.bookingId && it.nomisMovementSeq == nomisMovementId.sequence }?.dpsCourtMovementId
  }

  private fun CourtSchedulerPrisonerMappingIdsDto.unexpectedDpsMovements(
    dpsMovementIds: List<UUID>,
    nomisMovementIds: List<MovementId>,
  ) = findMissing(dpsMovementIds, nomisMovementIds) { dpsMovementId ->
    movements.find { it.dpsCourtMovementId == dpsMovementId }?.let { MovementId(it.nomisBookingId, it.nomisMovementSeq) }
  }

  private fun CourtSchedulerPrisonerMappingIdsDto.matchingSchedules(
    nomisCourtEventIds: List<Long>,
    dpsCourtEventIds: List<UUID>,
  ) = findMatches(nomisCourtEventIds, dpsCourtEventIds) { nomisId ->
    schedules.find { it.nomisEventId == nomisId }?.dpsCourtAppearanceId
  }

  private fun CourtSchedulerPrisonerMappingIdsDto.matchingMovements(
    nomisMovementIds: List<MovementId>,
    dpsMovementIds: List<UUID>,
  ) = findMatches(nomisMovementIds, dpsMovementIds) { nomisId ->
    movements.find { it.nomisBookingId == nomisId.bookingId && it.nomisMovementSeq == nomisId.sequence }?.dpsCourtMovementId
  }

  private suspend fun List<BookingCourtMovements>.findCourtSentencingIds() = flatMap { it.courtSchedules }
    .filter { it.courtCaseId != null }
    .takeIf { it.isNotEmpty() }
    ?.map { it.eventId }
    ?.let { courtSentencingMappingService.getAllCourtAppearancesByNomisIds(it) }
    ?.associate { it.nomisCourtAppearanceId to it.dpsCourtAppearanceId }
    ?: emptyMap()

  private fun OffenderCourtMovementsResponse.findNomisCourtSchedule(eventId: Long) = bookings.flatMap { it.courtSchedules }
    .find { it.eventId == eventId }
    ?: throw IllegalStateException("Unable to find schedule for eventId=$eventId despite having matched it earlier. Has there been a merge or move booking mid reconciliation?")

  private fun ReconciliationResponse.findDpsCourtSchedule(dpsId: UUID) = courtEvents.find { it.courtEvent.dpsId == dpsId }
    ?: throw IllegalStateException("Unable to find schedule for dpsId=$dpsId despite having matched it earlier. Has there been a merge or move court event mid reconciliation?")

  private fun OffenderCourtMovementsResponse.findNomisScheduledMovementOut(movementId: MovementId) = bookings.flatMap { booking ->
    booking.courtSchedules
      .mapNotNull { it.courtMovementOut }
      .map { it to booking.bookingId }
  }.find { it.second == movementId.bookingId && it.first.sequence == movementId.sequence }!!.first

  private fun ReconciliationResponse.findDpsCourtMovementOut(id: UUID) = courtEvents.flatMap { courtEvent ->
    courtEvent.movements.filter { it.directionCode == "OUT" }
  }.first { it.dpsId == id }

  private fun OffenderCourtMovementsResponse.findNomisScheduledMovementIn(movementId: MovementId) = bookings.flatMap { booking ->
    booking.courtSchedules
      .mapNotNull { it.courtMovementIn }
      .map { it to booking.bookingId }
  }.find { it.second == movementId.bookingId && it.first.sequence == movementId.sequence }!!.first

  private fun ReconciliationResponse.findDpsCourtMovementIn(id: UUID) = courtEvents.flatMap { courtEvent ->
    courtEvent.movements.filter { it.directionCode == "IN" }
  }.first { it.dpsId == id }

  private fun OffenderCourtMovementsResponse.findNomisUnscheduledMovementOut(movementId: MovementId) = bookings.flatMap { booking ->
    booking.unscheduledCourtMovementOuts
      .map { it to booking.bookingId }
  }.find { it.second == movementId.bookingId && it.first.sequence == movementId.sequence }!!.first

  private fun OffenderCourtMovementsResponse.findNomisUnscheduledMovementIn(movementId: MovementId) = bookings.flatMap { booking ->
    booking.unscheduledCourtMovementIns
      .map { it to booking.bookingId }
  }.find { it.second == movementId.bookingId && it.first.sequence == movementId.sequence }!!.first

  private fun ReconciliationResponse.findDpsUnscheduledMovementOut(id: UUID) = unscheduledMovements
    .filter { it.directionCode == "OUT" }
    .first { it.dpsId == id }

  private fun ReconciliationResponse.findDpsUnscheduledMovementIn(id: UUID) = unscheduledMovements
    .filter { it.directionCode == "IN" }
    .first { it.dpsId == id }
}

abstract class MismatchedPrisonerCourtMovements(
  val offenderNo: String,
  val type: String,
)

internal class MismatchedPrisonerCourtMovementIds(
  offenderNo: String,
  type: Type,
  val nomisCount: Int,
  val dpsCount: Int,
  val unexpectedNomisIds: String,
  val unexpectedDpsIds: String,
) : MismatchedPrisonerCourtMovements(offenderNo, type.name) {
  enum class Type {
    SCHEDULE,
    SCHEDULED_MOVEMENT_OUT,
    UNSCHEDULED_MOVEMENT_OUT,
    SCHEDULED_MOVEMENT_IN,
    UNSCHEDULED_MOVEMENT_IN,
    MISSING_MAPPING_SCHEDULE,
    MISSING_MAPPING_SCHEDULED_MOVEMENT_OUT,
    MISSING_MAPPING_UNSCHEDULED_MOVEMENT_OUT,
    MISSING_MAPPING_SCHEDULED_MOVEMENT_IN,
    MISSING_MAPPING_UNSCHEDULED_MOVEMENT_IN,
  }
}

internal class MismatchPrisonerCourtScheduleDetails(
  offenderNo: String,
  type: Type,
  val nomisEventId: Long,
  val dpsCourtEventId: UUID,
  val nomisValue: String,
  val dpsValue: String,
) : MismatchedPrisonerCourtMovements(offenderNo, type.name) {
  enum class Type {
    COURT,
    EVENT_STATUS,
    EVENT_TYPE,
    EXTERNAL_REFERENCE_URN,
    PRISON,
    START_TIME,
  }
}

internal class MismatchPrisonerCourtMovementDetails(
  offenderNo: String,
  type: Type,
  val nomisMovementId: MovementId,
  val dpsCourtMovementId: UUID,
  val nomisValue: String,
  val dpsValue: String,
) : MismatchedPrisonerCourtMovements(offenderNo, type.name) {
  enum class Type {
    COURT,
    MOVEMENT_TIME,
    PRISON,
    REASON,
  }
}

internal class MovementId(val bookingId: Long, val sequence: Int) {
  override fun toString(): String = "${bookingId}_$sequence"
}
