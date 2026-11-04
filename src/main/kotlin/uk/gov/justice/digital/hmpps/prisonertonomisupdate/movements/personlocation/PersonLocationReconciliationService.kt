package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
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
  private val prisonerApi: NomisApiService,
  @param:Value($$"${reports.person-location.active-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.person-location.active-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_PERSON_LOCATION = "person-location-reconciliation"
  }

  suspend fun generatePersonLocationReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_LOCATION-requested",
      mapOf(),
    )

    runCatching { generatePersonLocationReconciliationReport() }
      .onSuccess {
        log.info("Person location reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_PERSON_LOCATION-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_PERSON_LOCATION-report", mapOf("success" to "false"))
        log.error("Person location prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerId>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generatePersonLocationReconciliationReport(): ReconciliationResult<PrisonerId> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonersMatch,
    nextPage = ::getNextPrisonersForPage,
  )

  private suspend fun getNextPrisonersForPage(lastOffenderId: Long): ReconciliationPageResult<PrisonerId> = runCatching {
    prisonerApi.getAllPrisoners(fromId = lastOffenderId, pageSize = pageSize)
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_LOCATION-mismatch-page-error",
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
    log.error("Unable to match person locations for prisoner ${prisonerId.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PERSON_LOCATION-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
    )
  }.getOrNull()

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
          "$TELEMETRY_PERSON_LOCATION-mismatch",
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
          "$TELEMETRY_PERSON_LOCATION-mismatch",
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
          "$TELEMETRY_PERSON_LOCATION-mismatch",
          mapOf(
            "offenderNo" to offenderNo,
            "type" to it.type,
            "nomisBookingId" to "${it.nomisBookingId}",
            "dpsSeriesId" to "${it.dpsSeriesId}",
          ),
        )
      }
    }

    return buildList {
      addAll(mismatchedEntities)
      addAll(missingMappings)
      addAll(mismatchedMovements)
      addAll(mismatchedBookings)
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
