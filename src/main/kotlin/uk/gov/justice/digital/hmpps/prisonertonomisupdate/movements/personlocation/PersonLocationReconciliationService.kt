package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationNames
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.NomisMovementId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.findMatches
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.findMissing
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_ACTIVE_FLAG
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_BEGIN_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_END_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_NUMBER
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_STATUS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.BOOKING
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MISSING_MAPPING_BOOKING
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MISSING_MAPPING_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_COMMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_DIRECTION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_FROM_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_REASON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TO_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TYPE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.SuspectedCorruption.LATEST_MOVEMENT_ON_OTHER_BOOKING
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.SuspectedCorruption.MISSING_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.SuspectedCorruption.MOVEMENTS_OUT_OF_ORDER
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.SuspectedCorruption.STATUS_CONFLICTS_WITH_LAST_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.STATUS_ACTIVE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.STATUS_IN_OUT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.STATUS_PRISON_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingPersonLocations
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationBooking
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.DirectionCode.IN
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.ADM
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.CRT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.REL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.CodedReason
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.ReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalJourney.Type.ADMISSION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalJourney.Type.COURT_APPEARANCE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalJourney.Type.RELEASE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalJourney.Type.TEMPORARY_ABSENCE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalJourney.Type.TRANSFER
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalMovement.Type.ARRIVAL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalMovement.Type.DEPARTURE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.util.*

@Service
class PersonLocationReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApi: PersonLocationNomisApiService,
  private val dpsApi: PersonLocationDpsApiService,
  private val mappingApi: PersonLocationMappingApiService,
  private val nomisPrisonerApi: NomisApiService,
  @param:Value($$"${reports.person-location.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.person-location.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  private companion object {
    const val GHOST_PRISON = "ZZGHI"
  }

  private val names = ReconciliationNames("person-location-reconciliation", "Person location")

  suspend fun generateReport(selection: PrisonerSelection) = PrisonerReconciliationReport(telemetryClient, nomisPrisonerApi, names, selection, pageSize, threadCount) { checkPrisonersMatch(it) }.run()

  suspend fun checkPrisonersMatch(offenderNo: String, suppressTelemetry: Boolean = false): List<MismatchedPrisonerLocations> = withContext(Dispatchers.Unconfined) {
    val nomisLocations = async { nomisApi.getOffenderPersonLocationsOrNull(offenderNo) }
    val dpsLocations = async { dpsApi.getReconciliation(offenderNo) }
    val nomis = nomisLocations.await()
      ?: throw IllegalStateException("Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?")

    if (nomis.bookings.any { it.booking.latestBooking && it.booking.prison == GHOST_PRISON }) {
      dpsLocations.cancel()
      return@withContext emptyList()
    }

    val mappings = mappingApi.getMappings(nomis.bookings.map { it.booking.bookingId })
    checkPrisonersMatch(offenderNo, nomis, dpsLocations.await(), mappings, suppressTelemetry)
  }

  private fun checkPrisonersMatch(
    offenderNo: String,
    nomisLocations: OffenderPersonLocationsResponse,
    dpsLocations: ReconciliationResponse,
    mappings: List<PersonLocationBookingMappingDto>,
    suppressTelemetry: Boolean,
  ): List<MismatchedPrisonerLocations> {
    val mismatchedEntities = findMismatchedEntities(offenderNo, nomisLocations, dpsLocations, mappings)
    val missingMappings = findMissingMappings(offenderNo, nomisLocations, dpsLocations, mappings)
    if (!suppressTelemetry) {
      (mismatchedEntities + missingMappings).forEach {
        telemetryClient.trackEvent(
          "${names.telemetryPrefix}-mismatch",
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

    val mismatchedMovements = findMismatchedMovements(offenderNo, nomisLocations, dpsLocations, mappings)
    if (!suppressTelemetry) {
      mismatchedMovements.forEach {
        telemetryClient.trackEvent(
          "${names.telemetryPrefix}-mismatch",
          mapOf(
            "offenderNo" to offenderNo,
            "type" to it.type,
            "nomisMovementId" to "${it.nomisMovementId}",
            "dpsMovementId" to "${it.dpsMovementId}",
          ),
        )
      }
    }

    val mismatchedBookings = findMismatchedBookings(offenderNo, nomisLocations, dpsLocations, mappings)
    if (!suppressTelemetry) {
      mismatchedBookings.forEach {
        telemetryClient.trackEvent(
          "${names.telemetryPrefix}-mismatch",
          mapOf(
            "offenderNo" to offenderNo,
            "type" to it.type,
            "nomisBookingId" to "${it.nomisBookingId}",
            "dpsSeriesId" to "${it.dpsSeriesId}",
          ),
        )
      }
    }

    val mismatchedStatus = findMismatchedStatus(offenderNo, nomisLocations, dpsLocations)
    if (!suppressTelemetry) {
      mismatchedStatus.forEach {
        telemetryClient.trackEvent(
          "${names.telemetryPrefix}-mismatch",
          buildMap {
            put("offenderNo", offenderNo)
            put("type", it.type)
            put("nomisBookingId", "${it.nomisBookingId}")
            it.suspectedCorruption?.let { reasons -> put("suspectedCorruption", reasons) }
          },
        )
      }
    }

    return buildList {
      addAll(mismatchedEntities)
      addAll(missingMappings)
      addAll(mismatchedMovements)
      addAll(mismatchedBookings)
      addAll(mismatchedStatus)
    }
  }

  private fun findMismatchedEntities(
    offenderNo: String,
    nomisLocations: OffenderPersonLocationsResponse,
    dpsLocations: ReconciliationResponse,
    mappings: List<PersonLocationBookingMappingDto>,
  ): List<MismatchedPrisonerLocationIds> {
    val mismatches = mutableListOf<MismatchedPrisonerLocationIds>()

    // Check for bookings only in 1 system
    val nomisBookingIds = nomisLocations.bookings.map { it.booking.bookingId }
    val dpsSeriesIds = dpsLocations.series.map { it.series.dpsId!! }
    if (nomisBookingIds.size != dpsSeriesIds.size) {
      mismatches.add(
        MismatchedPrisonerLocationIds(
          offenderNo = offenderNo,
          type = BOOKING,
          nomisCount = nomisBookingIds.size,
          dpsCount = dpsSeriesIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisBookings(nomisBookingIds, dpsSeriesIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsSeries(dpsSeriesIds, nomisBookingIds).toString(),
        ),
      )
    }

    // Check for movements only in 1 system
    val nomisMovementIds = nomisLocations.movementIds()
    val dpsMovementIds = dpsLocations.movementIds()
    if (nomisMovementIds.size != dpsMovementIds.size) {
      mismatches.add(
        MismatchedPrisonerLocationIds(
          offenderNo = offenderNo,
          type = MOVEMENT,
          nomisCount = nomisMovementIds.size,
          dpsCount = dpsMovementIds.size,
          unexpectedNomisIds = mappings.unexpectedNomisMovements(nomisMovementIds, dpsMovementIds).toString(),
          unexpectedDpsIds = mappings.unexpectedDpsMovements(dpsMovementIds, nomisMovementIds).toString(),
        ),
      )
    }

    return mismatches
  }

  fun List<PersonLocationBookingMappingDto>.unexpectedNomisBookings(
    nomisBookingIds: List<Long>,
    dpsSeriesIds: List<UUID>,
  ) = findMissing(nomisBookingIds, dpsSeriesIds) { nomisId ->
    this.find { it.bookingId == nomisId }?.dpsCustodialSeriesId
  }

  fun List<PersonLocationBookingMappingDto>.unexpectedDpsSeries(
    dpsSeriesIds: List<UUID>,
    nomisBookingIds: List<Long>,
  ) = findMissing(dpsSeriesIds, nomisBookingIds) { dpsId ->
    this.find { it.dpsCustodialSeriesId == dpsId }?.bookingId
  }

  fun List<PersonLocationBookingMappingDto>.unexpectedNomisMovements(
    nomisMovementIds: List<NomisMovementId>,
    dpsMovementIds: List<UUID>,
  ) = findMissing(nomisMovementIds, dpsMovementIds) { nomisId ->
    this.find { it.bookingId == nomisId.bookingId }
      ?.movements?.find { it.nomisMovementSeq == nomisId.sequence }
      ?.dpsExternalMovementId
  }

  fun List<PersonLocationBookingMappingDto>.unexpectedDpsMovements(
    dpsMovementIds: List<UUID>,
    nomisMovementIds: List<NomisMovementId>,
  ) = findMissing(dpsMovementIds, nomisMovementIds) { dpsId ->
    val bookingId = this.find { it.movements.any { it.dpsExternalMovementId == dpsId } }?.bookingId
    val seq = this.flatMap { it.movements }.find { it.dpsExternalMovementId == dpsId }?.nomisMovementSeq
    if (bookingId != null && seq != null) {
      NomisMovementId(bookingId, seq)
    } else {
      null
    }
  }

  private fun findMissingMappings(
    offenderNo: String,
    nomisLocations: OffenderPersonLocationsResponse,
    dpsLocations: ReconciliationResponse,
    mappings: List<PersonLocationBookingMappingDto>,
  ): List<MismatchedPrisonerLocationIds> {
    val mismatches = mutableListOf<MismatchedPrisonerLocationIds>()

    // check for missing booking mappings
    val nomisBookingIds = nomisLocations.bookings.map { it.booking.bookingId }
    val dpsSeriesIds = dpsLocations.series.map { it.series.dpsId!! }
    val matchedBookingIds = mappings.matchingBookings(nomisBookingIds, dpsSeriesIds)
    if (matchedBookingIds.size != nomisBookingIds.size || matchedBookingIds.size != dpsSeriesIds.size) {
      val missingNomisIds = nomisBookingIds - matchedBookingIds.map { it.first }.toSet()
      val missingDpsIds = dpsSeriesIds - matchedBookingIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerLocationIds(
          offenderNo = offenderNo,
          type = MISSING_MAPPING_BOOKING,
          nomisCount = nomisBookingIds.size,
          dpsCount = dpsSeriesIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    // check for missing movement mappings
    val nomisMovementIds = nomisLocations.movementIds()
    val dpsMovementIds = dpsLocations.movementIds()
    val matchedMovementIds = mappings.matchingMovements(nomisMovementIds, dpsMovementIds)
    if (matchedMovementIds.size != nomisMovementIds.size || matchedMovementIds.size != dpsMovementIds.size) {
      val missingNomisIds = nomisMovementIds - matchedMovementIds.map { it.first }.toSet()
      val missingDpsIds = dpsMovementIds - matchedMovementIds.map { it.second }.toSet()
      mismatches.add(
        MismatchedPrisonerLocationIds(
          offenderNo = offenderNo,
          type = MISSING_MAPPING_MOVEMENT,
          nomisCount = nomisMovementIds.size,
          dpsCount = dpsMovementIds.size,
          unexpectedNomisIds = "$missingNomisIds",
          unexpectedDpsIds = "$missingDpsIds",
        ),
      )
    }

    return mismatches
  }

  private fun List<PersonLocationBookingMappingDto>.matchingBookings(
    nomisBookingIds: List<Long>,
    dpsSeriesIds: List<UUID>,
  ) = findMatches(nomisBookingIds, dpsSeriesIds) { nomisId ->
    this.find { it.bookingId == nomisId }?.dpsCustodialSeriesId
  }

  private fun List<PersonLocationBookingMappingDto>.matchingMovements(
    nomisMovementIds: List<NomisMovementId>,
    dpsMovementIds: List<UUID>,
  ) = findMatches(nomisMovementIds, dpsMovementIds) { nomisId ->
    this.find { it.bookingId == nomisId.bookingId }
      ?.movements?.find { it.nomisMovementSeq == nomisId.sequence }
      ?.dpsExternalMovementId
  }

  private fun findMismatchedMovements(
    offenderNo: String,
    nomisLocations: OffenderPersonLocationsResponse,
    dpsLocations: ReconciliationResponse,
    mappings: List<PersonLocationBookingMappingDto>,
  ): List<MismatchedPrisonerLocationMovements> {
    val mismatches = mutableListOf<MismatchedPrisonerLocationMovements>()
    val nomisMovementIds = nomisLocations.movementIds()
    val dpsMovementIds = dpsLocations.movementIds()

    mappings.matchingMovements(nomisMovementIds, dpsMovementIds).forEach { (nomisId, dpsId) ->
      fun mismatch(type: MismatchedPrisonerLocationMovements.Type, nomisValue: String?, dpsValue: String?) = MismatchedPrisonerLocationMovements(offenderNo, type, nomisId, dpsId, nomisValue, dpsValue)
      val nomisMovement = nomisLocations.findMovement(nomisId)
      val dpsMovement = dpsLocations.findMovement(dpsId)

      // comments
      if (nomisMovement.commentText != dpsMovement.notes) {
        mismatches.add(mismatch(MOVEMENT_COMMENT, nomisMovement.commentText, dpsMovement.notes))
      }

      // direction
      if (nomisMovement.directionCode.toDpsMovementType() != dpsMovement.type) {
        mismatches.add(mismatch(MOVEMENT_DIRECTION, nomisMovement.directionCode.value, dpsMovement.type.value))
      }

      // from location
      val acceptableOutsideFrom = nomisMovement.directionCode == IN && nomisMovement.from == null && dpsMovement.from.description == "Outside"
      if (!acceptableOutsideFrom && !nomisMovement.hasMutableTapAddressFrom() && nomisMovement.from?.expectedDpsDescription() != dpsMovement.from.description) {
        mismatches.add(mismatch(MOVEMENT_FROM_LOCATION, nomisMovement.from?.description, dpsMovement.from.description))
      }

      // movement reason
      if (nomisMovement.movementReason.code != (dpsMovement.reason as CodedReason).code) {
        mismatches.add(mismatch(MOVEMENT_REASON, nomisMovement.movementReason.code, dpsMovement.reason.code))
      }

      // movement time - NOMIS overnight job TAG_DATETIME_CORRECTION_UTILITY can zero the seconds
      if (nomisMovement.movementTime.withSecond(0) != dpsMovement.occurredAt.withSecond(0)) {
        mismatches.add(mismatch(MOVEMENT_TIME, "${nomisMovement.movementTime}", "${dpsMovement.occurredAt}"))
      }

      // movement type
      if (nomisMovement.movementType.toDpsJourneyType() != dpsMovement.journey.type) {
        mismatches.add(mismatch(MOVEMENT_TYPE, nomisMovement.movementType.value, dpsMovement.journey.type.value))
      }

      // to location
      if (!nomisMovement.hasMutableTapAddressTo() && nomisMovement.to?.expectedDpsDescription() != dpsMovement.to?.description) {
        mismatches.add(mismatch(MOVEMENT_TO_LOCATION, nomisMovement.to?.description, dpsMovement.to?.description))
      }
    }

    return mismatches
  }

  private fun findMismatchedBookings(
    offenderNo: String,
    nomisLocations: OffenderPersonLocationsResponse,
    dpsLocations: ReconciliationResponse,
    mappings: List<PersonLocationBookingMappingDto>,
  ): List<MismatchedPrisonerLocationBookings> {
    val mismatches = mutableListOf<MismatchedPrisonerLocationBookings>()
    val nomisBookingIds = nomisLocations.bookings.map { it.booking.bookingId }
    val dpsSeriesIds = dpsLocations.series.map { it.series.dpsId!! }

    mappings.matchingBookings(nomisBookingIds, dpsSeriesIds).forEach { (nomisId, dpsId) ->
      fun mismatch(type: MismatchedPrisonerLocationBookings.Type, nomisValue: String?, dpsValue: String?) = MismatchedPrisonerLocationBookings(offenderNo, type, nomisId, dpsId, nomisValue, dpsValue)

      val nomisMovement = nomisLocations.bookings.first { it.booking.bookingId == nomisId }.booking
      val dpsMovement = dpsLocations.series.first { it.series.dpsId!! == dpsId }.series

      // booking status
      if (nomisMovement.bookingStatus.value != dpsMovement.status.value) {
        mismatches.add(mismatch(BOOKING_STATUS, nomisMovement.bookingStatus.value, dpsMovement.status.value))
      }

      // active flag
      if (nomisMovement.activeBooking != dpsMovement.isActive) {
        mismatches.add(mismatch(BOOKING_ACTIVE_FLAG, "${nomisMovement.activeBooking}", "${dpsMovement.isActive}"))
      }

      // booking begin time
      if (nomisMovement.bookingBeginTime != dpsMovement.openedAt) {
        mismatches.add(mismatch(BOOKING_BEGIN_TIME, "${nomisMovement.bookingBeginTime}", "${dpsMovement.openedAt}"))
      }

      // booking end time
      if (nomisMovement.bookingEndTime != dpsMovement.closedAt) {
        mismatches.add(mismatch(BOOKING_END_TIME, "${nomisMovement.bookingEndTime}", "${dpsMovement.closedAt}"))
      }

      // booking number
      if (nomisMovement.bookingNumber != dpsMovement.legacyBookingReference) {
        mismatches.add(mismatch(BOOKING_NUMBER, nomisMovement.bookingNumber, dpsMovement.legacyBookingReference))
      }
    }

    return mismatches
  }

  private fun findMismatchedStatus(
    offenderNo: String,
    nomisLocations: OffenderPersonLocationsResponse,
    dpsLocations: ReconciliationResponse,
  ): List<MismatchedPrisonerLocationStatus> {
    val nomisBookingLocations = nomisLocations.bookings.let { bookings -> bookings.firstOrNull { it.booking.activeBooking } ?: bookings.firstOrNull { it.booking.latestBooking } }
    val nomisBooking = nomisBookingLocations?.booking
    val dpsStatus = dpsLocations.status

    class StatusCheck(val type: MismatchedPrisonerLocationStatus.Type, val nomisValue: String?, val expectedDpsValue: String?, val dpsValue: String?)

    val checks = nomisBooking?.let {
      val nomisActive = if (it.activeBooking) "ACTIVE" else "INACTIVE"
      listOf(
        StatusCheck(STATUS_ACTIVE, nomisActive, nomisActive, dpsStatus.expectedLegacyActive),
        StatusCheck(STATUS_IN_OUT, it.inOutStatus, it.inOutStatus, dpsStatus.expectedLegacyInOut),
        // NOMIS uses OUT and TRN in place of a prison when the prisoner is not in a prison
        StatusCheck(STATUS_PRISON_CODE, it.prison, it.prison.takeUnless { prison -> prison in listOf("OUT", "TRN") }, dpsStatus.prisonCode),
      )
    } ?: listOf(
      // a prisoner with no NOMIS bookings, e.g. after a merge not yet processed by DPS
      StatusCheck(STATUS_ACTIVE, null, "INACTIVE", dpsStatus.expectedLegacyActive),
      StatusCheck(STATUS_IN_OUT, null, "OUT", dpsStatus.expectedLegacyInOut),
      StatusCheck(STATUS_PRISON_CODE, null, null, dpsStatus.prisonCode),
    )

    val suspectedCorruption by lazy { nomisBookingLocations?.suspectedCorruption(otherBookings = nomisLocations.bookings - nomisBookingLocations) }
    return checks
      .filter { it.expectedDpsValue != it.dpsValue }
      .map { MismatchedPrisonerLocationStatus(offenderNo, it.type, nomisBooking?.bookingId, it.nomisValue, it.dpsValue, suspectedCorruption) }
  }

  // Status mismatches are often caused by corrupt NOMIS data where the booking status disagrees with its movements
  private fun BookingPersonLocations.suspectedCorruption(otherBookings: List<BookingPersonLocations>): String? {
    val movementsBySequence = movements.sortedBy { it.sequence }
    val latestMovementTime = movements.maxOfOrNull { it.movementTime }
    return listOfNotNull(
      STATUS_CONFLICTS_WITH_LAST_MOVEMENT.takeIf { movementsBySequence.lastOrNull()?.let { booking.conflictsWithLastMovement(it) } == true },
      MOVEMENTS_OUT_OF_ORDER.takeIf { movementsBySequence != movements.sortedWith(compareBy({ it.movementTime }, { it.sequence })) },
      MISSING_MOVEMENT.takeIf { movementsBySequence.zipWithNext().any { (previous, next) -> previous.directionCode == next.directionCode } },
      LATEST_MOVEMENT_ON_OTHER_BOOKING.takeIf { otherBookings.flatMap { it.movements }.any { latestMovementTime == null || it.movementTime > latestMovementTime } },
    )
      .takeIf { it.isNotEmpty() }
      ?.joinToString(",")
  }

  private fun PersonLocationBooking.conflictsWithLastMovement(lastMovement: PersonLocationMovement): Boolean {
    val (expectedActive, expectedInOut, expectedPrison) = when {
      lastMovement.directionCode == IN -> Triple(true, "IN", lastMovement.to?.code)
      lastMovement.movementType == REL -> Triple(false, "OUT", "OUT")
      lastMovement.movementType == TRN -> Triple(true, "TRN", "TRN")
      else -> Triple(true, "OUT", lastMovement.from?.code)
    }
    return activeBooking != expectedActive || inOutStatus != expectedInOut || prison != expectedPrison
  }

  // The sync sends DPS "UNKNOWN" rather than the NOMIS descriptions of the unknown location codes
  private fun MovementLocation.expectedDpsDescription() = if (code in listOf("UNKNWN", "UNKNCT")) "UNKNOWN" else description

  // NOMIS allows the address of a historical TAP to be changed, but DPS treats it as immutable so it can't be reconciled
  private fun PersonLocationMovement.hasMutableTapAddressFrom() = movementType == TAP && directionCode == IN && from?.type == MovementLocation.Type.OTHER
  private fun PersonLocationMovement.hasMutableTapAddressTo() = movementType == TAP && directionCode == OUT && to?.type == MovementLocation.Type.OTHER

  private fun OffenderPersonLocationsResponse.movementIds() = bookings.flatMap { bk -> bk.movements.map { mv -> NomisMovementId(bk.booking.bookingId, mv.sequence) } }
  private fun OffenderPersonLocationsResponse.findMovement(nomisId: NomisMovementId) = bookings.flatMap { it.movements }.first { it.bookingId == nomisId.bookingId && it.sequence == nomisId.sequence }
  private fun ReconciliationResponse.movementIds() = series.flatMap { ser -> ser.movements.map { mv -> mv.dpsId!! } }
  private fun ReconciliationResponse.findMovement(dpsId: UUID) = series.flatMap { it.movements }.first { it.dpsId == dpsId }
}

abstract class MismatchedPrisonerLocations(
  val offenderNo: String,
  val type: String,
)

class MismatchedPrisonerLocationIds(
  offenderNo: String,
  type: Type,
  val nomisCount: Int,
  val dpsCount: Int,
  val unexpectedNomisIds: String,
  val unexpectedDpsIds: String,
) : MismatchedPrisonerLocations(offenderNo, type.name) {
  override fun toString() = "MismatchedPrisonerLocationIds(offenderNo=$offenderNo, type=$type, nomisCount=$nomisCount, dpsCount=$dpsCount, unexpectedNomisIds=$unexpectedNomisIds, unexpectedDpsIds=$unexpectedDpsIds)"

  enum class Type {
    BOOKING,
    MOVEMENT,
    MISSING_MAPPING_BOOKING,
    MISSING_MAPPING_MOVEMENT,
  }
}

class MismatchedPrisonerLocationMovements(
  offenderNo: String,
  type: Type,
  val nomisMovementId: NomisMovementId,
  val dpsMovementId: UUID,
  val nomisValue: String?,
  val dpsValue: String?,
) : MismatchedPrisonerLocations(offenderNo, type.name) {
  override fun toString() = "MismatchedPrisonerLocationMovements(offenderNo=$offenderNo, type=$type, nomisMovementId=$nomisMovementId, dpsMovementId=$dpsMovementId, nomisValue=$nomisValue, dpsValue=$dpsValue)"

  enum class Type {
    MOVEMENT_COMMENT,
    MOVEMENT_DIRECTION,
    MOVEMENT_FROM_LOCATION,
    MOVEMENT_REASON,
    MOVEMENT_TIME,
    MOVEMENT_TYPE,
    MOVEMENT_TO_LOCATION,
  }
}

class MismatchedPrisonerLocationBookings(
  offenderNo: String,
  type: Type,
  val nomisBookingId: Long,
  val dpsSeriesId: UUID,
  val nomisValue: String?,
  val dpsValue: String?,
) : MismatchedPrisonerLocations(offenderNo, type.name) {
  override fun toString() = "MismatchedPrisonerLocationBookings(offenderNo=$offenderNo, type=$type, nomisBookingId=$nomisBookingId, dpsSeriesId=$dpsSeriesId, nomisValue=$nomisValue, dpsValue=$dpsValue)"

  enum class Type {
    BOOKING_ACTIVE_FLAG,
    BOOKING_BEGIN_TIME,
    BOOKING_END_TIME,
    BOOKING_NUMBER,
    BOOKING_STATUS,
  }
}

private fun PersonLocationMovement.MovementType.toDpsJourneyType() = when (this) {
  ADM -> ADMISSION
  CRT -> COURT_APPEARANCE
  REL -> RELEASE
  TAP -> TEMPORARY_ABSENCE
  TRN -> TRANSFER
}

private fun PersonLocationMovement.DirectionCode.toDpsMovementType() = when (this) {
  IN -> ARRIVAL
  OUT -> DEPARTURE
}

class MismatchedPrisonerLocationStatus(
  offenderNo: String,
  type: Type,
  val nomisBookingId: Long?,
  val nomisValue: String?,
  val dpsValue: String?,
  val suspectedCorruption: String? = null,
) : MismatchedPrisonerLocations(offenderNo, type.name) {
  override fun toString() = "MismatchedPrisonerLocationStatus(offenderNo=$offenderNo, type=$type, nomisBookingId=$nomisBookingId, nomisValue=$nomisValue, dpsValue=$dpsValue, suspectedCorruption=$suspectedCorruption)"

  enum class Type {
    STATUS_ACTIVE,
    STATUS_IN_OUT,
    STATUS_PRISON_CODE,
  }

  enum class SuspectedCorruption {
    STATUS_CONFLICTS_WITH_LAST_MOVEMENT,
    MOVEMENTS_OUT_OF_ORDER,
    MISSING_MOVEMENT,
    LATEST_MOVEMENT_ON_OTHER_BOOKING,
  }
}
