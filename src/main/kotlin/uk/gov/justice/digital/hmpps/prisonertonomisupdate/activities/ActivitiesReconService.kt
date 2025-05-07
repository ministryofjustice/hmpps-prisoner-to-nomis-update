package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import java.time.LocalDate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AllocationReconciliationResponse as DpsAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceReconciliationResponse as DpsAttendanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AllocationReconciliationResponse as NomisAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AttendanceReconciliationResponse as NomisAttendanceResponse

@Service
class ActivitiesReconService(
  private val telemetryClient: TelemetryClient,
  private val activitiesNomisApiService: ActivitiesNomisApiService,
  private val reportScope: CoroutineScope,
  private val activitiesApiService: ActivitiesApiService,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  suspend fun allocationReconciliationReport() {
    val prisons = try {
      activitiesNomisApiService.getServicePrisons("ACTIVITY")
        .also {
          telemetryClient.trackEvent(
            "activity-allocation-reconciliation-report-requested",
            mapOf("prisons" to it.map { prison -> prison.prisonId }.toString()),
          )
        }
    } catch (e: Exception) {
      telemetryClient.trackEvent("activity-allocation-reconciliation-report-error", mapOf())
      throw e
    }

    prisons.forEach { reportScope.launch { allocationsReconciliationReport(it.prisonId) } }
  }

  suspend fun allocationsReconciliationReport(prisonId: String) = try {
    coroutineScope {
      val nomisBookingCounts = async { activitiesNomisApiService.getAllocationReconciliation(prisonId) }
      val dpsBookingCounts = async { activitiesApiService.getAllocationReconciliation(prisonId) }
      val compareResults = compareBookingCounts(nomisBookingCounts.await(), dpsBookingCounts.await())

      publishTelemetry("allocation", prisonId, compareResults)
    }
  } catch (e: Exception) {
    log.error("Allocation reconciliation report failed for prison $prisonId", e)
    telemetryClient.trackEvent("activity-allocation-reconciliation-report-error", mapOf("prison" to prisonId))
  }

  suspend fun suspendedAllocationReconciliationReport() {
    val prisons = try {
      activitiesNomisApiService.getServicePrisons("ACTIVITY")
        .also {
          telemetryClient.trackEvent(
            "activity-suspended-allocation-reconciliation-report-requested",
            mapOf("prisons" to it.map { prison -> prison.prisonId }.toString()),
          )
        }
    } catch (e: Exception) {
      telemetryClient.trackEvent("activity-suspended-allocation-reconciliation-report-error", mapOf())
      throw e
    }

    prisons.forEach { reportScope.launch { suspendedAllocationsReconciliationReport(it.prisonId) } }
  }

  suspend fun suspendedAllocationsReconciliationReport(prisonId: String) = try {
    coroutineScope {
      val nomisBookingCounts = async { activitiesNomisApiService.getSuspendedAllocationReconciliation(prisonId) }
      val dpsBookingCounts = async { activitiesApiService.getSuspendedAllocationReconciliation(prisonId) }
      val compareResults = compareBookingCounts(nomisBookingCounts.await(), dpsBookingCounts.await())

      publishTelemetry("suspended-allocation", prisonId, compareResults)
    }
  } catch (e: Exception) {
    log.error("Suspended allocation reconciliation report failed for prison $prisonId", e)
    telemetryClient.trackEvent("activity-suspended-allocation-reconciliation-report-error", mapOf("prison" to prisonId))
  }

  suspend fun attendanceReconciliationReport(date: LocalDate) {
    val prisons = try {
      activitiesNomisApiService.getServicePrisons("ACTIVITY")
        .also {
          telemetryClient.trackEvent(
            "activity-attendance-reconciliation-report-requested",
            mapOf("prisons" to it.map { prison -> prison.prisonId }.toString(), "date" to "$date"),
          )
        }
    } catch (e: Exception) {
      telemetryClient.trackEvent("activity-attendance-reconciliation-report-error", mapOf("date" to "$date"))
      throw e
    }

    prisons.forEach { reportScope.launch { attendancesReconciliationReport(it.prisonId, date) } }
  }

  suspend fun attendancesReconciliationReport(prisonId: String, date: LocalDate) = try {
    coroutineScope {
      val nomisBookingCounts = async { activitiesNomisApiService.getAttendanceReconciliation(prisonId, date) }
      val dpsBookingCounts = async { activitiesApiService.getAttendanceReconciliation(prisonId, date) }
      val differences = compareBookingCounts(nomisBookingCounts.await(), dpsBookingCounts.await())

      publishTelemetry("attendance", prisonId, differences, date)
    }
  } catch (e: Exception) {
    log.error("Attendance reconciliation report failed for prison $prisonId", e)
    telemetryClient.trackEvent("activity-attendance-reconciliation-report-error", mapOf("prison" to prisonId, "date" to "$date"))
  }

  private suspend fun compareBookingCounts(nomisResults: NomisAllocationResponse, dpsResults: DpsAllocationResponse): CompareBookingsDifferences {
    val nomis = nomisResults.bookings.sortedBy { it.bookingId }.map { BookingCounts(it.bookingId, it.count) }
    val dps = dpsResults.bookings.sortedBy { it.bookingId }.map { BookingCounts(it.bookingId, it.count) }
    return compareBookingCounts(nomis, dps)
  }

  private suspend fun compareBookingCounts(nomisResults: NomisAttendanceResponse, dpsResults: DpsAttendanceResponse): CompareBookingsDifferences {
    val nomis = nomisResults.bookings.sortedBy { it.bookingId }.map { BookingCounts(it.bookingId, it.count) }
    val dps = dpsResults.bookings.sortedBy { it.bookingId }.map { BookingCounts(it.bookingId, it.count) }
    return compareBookingCounts(nomis, dps)
  }

  private suspend fun compareBookingCounts(nomis: List<BookingCounts>, dps: List<BookingCounts>): CompareBookingsDifferences {
    val nomisOnlyIds = nomis.map { it.id } - dps.map { it.id }.toSet()
    val dpsOnlyIds = dps.map { it.id } - nomis.map { it.id }.toSet()

    val both = nomis.filterNot { nomisOnlyIds.contains(it.id) }.zip(dps.filterNot { dpsOnlyIds.contains(it.id) })
    val differentCountIds = both.filter { it.first.count != it.second.count }.map { it.first.id }

    return CompareBookingsDifferences(
      nomisOnly = nomisOnlyIds,
      dpsOnly = dpsOnlyIds,
      differentCount = differentCountIds,
    )
  }

  private suspend fun publishTelemetry(
    type: String,
    prisonId: String,
    differences: CompareBookingsDifferences,
    date: LocalDate? = null,
  ) {
    val differencesWithDetails = with(differences.all()) {
      if (isNotEmpty()) activitiesNomisApiService.getPrisonerDetails(this) else listOf()
    }

    val ignoredBookings = differencesWithDetails
      .filter { it.location != prisonId }
      .onEach {
        log.info("Ignoring failed $type reconciliation for booking ${it.bookingId} at prison $prisonId as the prisoner is now at location ${it.location}")
      }
      .map { it.bookingId }

    // Publish telemetry for failures we're not ignoring
    differences.all()
      .filterNot { it in ignoredBookings }
      .sorted()
      .map { differencesWithDetails.first { details -> details.bookingId == it } }
      .forEach {
        telemetryClient.trackEvent(
          "activity-$type-reconciliation-report-failed",
          mutableMapOf(
            "prison" to prisonId,
            "type" to differences.differenceType(it.bookingId),
            "bookingId" to it.bookingId.toString(),
            "offenderNo" to it.offenderNo,
            "location" to it.location,
          ).apply {
            date?.also { this["date"] = "$it" }
          },
        )
      }

    // Publish success telemetry if all failures were ignored or there were no failures
    if (differences.all().size - ignoredBookings.size == 0) {
      telemetryClient.trackEvent(
        "activity-$type-reconciliation-report-success",
        mutableMapOf("prison" to prisonId).apply {
          date?.also { this["date"] = "$it" }
        },
      )
    }
  }
}

private data class CompareBookingsDifferences(
  val nomisOnly: List<Long>,
  val dpsOnly: List<Long>,
  val differentCount: List<Long>,
) {
  fun differenceType(bookingId: Long): String = when {
    nomisOnly.contains(bookingId) -> "NOMIS_only"
    dpsOnly.contains(bookingId) -> "DPS_only"
    differentCount.contains(bookingId) -> "different_count"
    else -> "no_differences"
  }
  fun all() = nomisOnly + dpsOnly + differentCount
}

private data class BookingCounts(val id: Long, val count: Long)
