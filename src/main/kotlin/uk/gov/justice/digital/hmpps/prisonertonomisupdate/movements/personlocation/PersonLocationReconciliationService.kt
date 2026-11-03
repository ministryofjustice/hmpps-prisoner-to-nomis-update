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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.findMissing
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.BOOKING
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.MismatchedPrisonerLocationIds.Type.MOVEMENT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.ReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.util.UUID

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

  suspend fun checkPrisonersMatch(offenderNo: String): List<MismatchedPrisonerLocations> = withContext(Dispatchers.Unconfined) {
    val nomisLocations = async { nomisApi.getOffenderPersonLocationsOrNull(offenderNo) }
    val dpsLocations = async { dpsApi.getReconciliation(offenderNo) }
    val mappings = nomisLocations.await()
      ?.let { async { mappingApi.getMappings(it.bookings.map { it.booking.bookingId }) } }
      ?: throw IllegalStateException("Cannot perform reconciliation for a prisoner that doesn't exist in NOMIS - has the prisoner been merged or deleted recently?")

    checkPrisonersMatch(offenderNo, nomisLocations.await()!!, dpsLocations.await(), mappings.await())
  }

  private fun checkPrisonersMatch(
    offenderNo: String,
    nomisLocations: OffenderPersonLocationsResponse,
    dpsLocations: ReconciliationResponse,
    mappings: List<PersonLocationBookingMappingDto>,
  ): List<MismatchedPrisonerLocations> {
    val mismatchedEntities = findMismatchedEntities(offenderNo, nomisLocations, dpsLocations, mappings)
    mismatchedEntities.forEach {
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

    return mismatchedEntities
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
    val nomisMovementIds = nomisLocations.bookings.flatMap { bk -> bk.movements.map { mv -> NomisMovementId(bk.booking.bookingId, mv.sequence) } }
    val dpsMovementIds = dpsLocations.series.flatMap { ser -> ser.movements.map { mv -> mv.dpsId!! } }
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
  enum class Type {
    BOOKING,
    MOVEMENT,
  }
}
