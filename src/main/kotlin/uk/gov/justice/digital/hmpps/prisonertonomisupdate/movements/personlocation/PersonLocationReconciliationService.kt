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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.ACTIVE_FLAG
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_BEGIN_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_END_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_NUMBER
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationBookings.Type.BOOKING_STATUS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.BOOKING
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MISSING_MAPPING_BOOKING
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MISSING_MAPPING_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.COMMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.DIRECTION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.FROM_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_REASON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TIME
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.MOVEMENT_TYPE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationMovements.Type.TO_LOCATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.ACTIVE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.IN_OUT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationStatus.Type.PRISON_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderPersonLocationsResponse
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
  private val names = ReconciliationNames("person-location-reconciliation", "Person location")

  suspend fun generateReport(selection: PrisonerSelection) = PrisonerReconciliationReport(telemetryClient, nomisPrisonerApi, names, selection, pageSize, threadCount) { checkPrisonersMatch(it) }.run()

  suspend fun checkPrisonersMatch(offenderNo: String, suppressTelemetry: Boolean = false): List<MismatchedPrisonerLocations> = withContext(Dispatchers.Unconfined) {
    val nomisLocations = async { nomisApi.getOffenderPersonLocationsOrNull(offenderNo) }
    val dpsLocations = async { dpsApi.getReconciliation(offenderNo) }
    val mappings = nomisLocations.await()
      ?.let { async { mappingApi.getMappings(it.bookings.map { it.booking.bookingId }) } }
      ?: throw IllegalStateException("Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?")

    checkPrisonersMatch(offenderNo, nomisLocations.await()!!, dpsLocations.await(), mappings.await(), suppressTelemetry)
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
          mapOf(
            "offenderNo" to offenderNo,
            "type" to it.type,
            "nomisBookingId" to "${it.nomisBookingId}",
            "nomisValue" to "${it.nomisValue}",
            "dpsValue" to "${it.dpsValue}",
          ),
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
        mismatches.add(mismatch(COMMENT, nomisMovement.commentText, dpsMovement.notes))
      }

      // direction
      if (nomisMovement.directionCode.toDpsMovementType() != dpsMovement.type) {
        mismatches.add(mismatch(DIRECTION, nomisMovement.directionCode.value, dpsMovement.type.value))
      }

      // from location
      if (nomisMovement.from?.description != dpsMovement.from.description) {
        mismatches.add(mismatch(FROM_LOCATION, nomisMovement.from?.description, dpsMovement.from.description))
      }

      // movement reason
      if (nomisMovement.movementReason.code != (dpsMovement.reason as CodedReason).code) {
        mismatches.add(mismatch(MOVEMENT_REASON, nomisMovement.movementReason.code, dpsMovement.reason.code))
      }

      // movement time
      if (nomisMovement.movementTime != dpsMovement.occurredAt) {
        mismatches.add(mismatch(MOVEMENT_TIME, "${nomisMovement.movementTime}", "${dpsMovement.occurredAt}"))
      }

      // movement type
      if (nomisMovement.movementType.toDpsJourneyType() != dpsMovement.journey.type) {
        mismatches.add(mismatch(MOVEMENT_TYPE, nomisMovement.movementType.value, dpsMovement.journey.type.value))
      }

      // to location
      if (nomisMovement.to?.description != dpsMovement.to?.description) {
        mismatches.add(mismatch(TO_LOCATION, nomisMovement.to?.description, dpsMovement.to?.description))
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
        mismatches.add(mismatch(ACTIVE_FLAG, "${nomisMovement.activeBooking}", "${dpsMovement.isActive}"))
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
    val nomisBooking = nomisLocations.bookings.map { it.booking }.let { bookings -> bookings.firstOrNull { it.activeBooking } ?: bookings.firstOrNull { it.latestBooking } }
    val dpsStatus = dpsLocations.status

    class StatusCheck(val type: MismatchedPrisonerLocationStatus.Type, val nomisValue: String?, val expectedDpsValue: String?, val dpsValue: String?)

    val checks = nomisBooking?.let {
      val nomisActive = if (it.activeBooking) "ACTIVE" else "INACTIVE"
      listOf(
        StatusCheck(ACTIVE, nomisActive, nomisActive, dpsStatus.expectedLegacyActive),
        StatusCheck(IN_OUT, it.inOutStatus, it.inOutStatus, dpsStatus.expectedLegacyInOut),
        // NOMIS uses OUT and TRN in place of a prison when the prisoner is not in a prison
        StatusCheck(PRISON_CODE, it.prison, it.prison.takeUnless { prison -> prison in listOf("OUT", "TRN") }, dpsStatus.prisonCode),
      )
    } ?: listOf(
      // a prisoner with no NOMIS bookings, e.g. after a merge not yet processed by DPS
      StatusCheck(ACTIVE, null, "OUT", dpsStatus.expectedLegacyActive),
      StatusCheck(IN_OUT, null, "OUT", dpsStatus.expectedLegacyInOut),
      StatusCheck(PRISON_CODE, null, null, dpsStatus.prisonCode),
    )

    return checks
      .filter { it.expectedDpsValue != it.dpsValue }
      .map { MismatchedPrisonerLocationStatus(offenderNo, it.type, nomisBooking?.bookingId, it.nomisValue, it.dpsValue) }
  }

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
    COMMENT,
    DIRECTION,
    FROM_LOCATION,
    MOVEMENT_REASON,
    MOVEMENT_TIME,
    MOVEMENT_TYPE,
    TO_LOCATION,
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
    ACTIVE_FLAG,
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
) : MismatchedPrisonerLocations(offenderNo, type.name) {
  override fun toString() = "MismatchedPrisonerLocationStatus(offenderNo=$offenderNo, type=$type, nomisBookingId=$nomisBookingId, nomisValue=$nomisValue, dpsValue=$dpsValue)"

  enum class Type {
    ACTIVE,
    IN_OUT,
    PRISON_CODE,
  }
}
