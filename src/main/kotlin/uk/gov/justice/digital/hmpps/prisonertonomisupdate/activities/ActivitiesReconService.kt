package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AllocationReconciliationResponse as DpsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AllocationReconciliationResponse as NomisResponse

@Service
class ActivitiesReconService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val reportScope: CoroutineScope,
  private val activitiesApiService: ActivitiesApiService,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  suspend fun allocationReconciliationReport() {
    val prisons = try {
      nomisApiService.getServicePrisons("ACTIVITY")
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

  suspend fun allocationsReconciliationReport(prisonId: String) =
    try {
      coroutineScope {
        val nomisBookingCounts = async { nomisApiService.getAllocationReconciliation(prisonId) }
        val dpsBookingCounts = async { activitiesApiService.getAllocationReconciliation(prisonId) }
        val compareResults = compareBookingCounts(nomisBookingCounts.await(), dpsBookingCounts.await())

        publishTelemetry(prisonId, compareResults)
      }
    } catch (e: Exception) {
      log.error("Allocation reconciliation report failed for prison $prisonId", e)
      telemetryClient.trackEvent("activity-allocation-reconciliation-report-error", mapOf("prison" to prisonId))
    }

  private suspend fun compareBookingCounts(nomisResults: NomisResponse, dpsResults: DpsResponse): CompareBookingsResult {
    val nomis = nomisResults.bookings.sortedBy { it.bookingId }.map { BookingCounts(it.bookingId, it.count) }
    val dps = dpsResults.bookings.sortedBy { it.bookingId }.map { BookingCounts(it.bookingId, it.count) }

    val nomisOnlyIds = nomis.map { it.id } - dps.map { it.id }.toSet()
    val dpsOnlyIds = dps.map { it.id } - nomis.map { it.id }.toSet()

    val both = nomis.filterNot { nomisOnlyIds.contains(it.id) }.zip(dps.filterNot { dpsOnlyIds.contains(it.id) })
    val differentCountIds = both.filter { it.first.count != it.second.count }.map { it.first.id }

    return CompareBookingsResult(
      nomisOnly = nomisOnlyIds,
      dpsOnly = dpsOnlyIds,
      differentCount = differentCountIds,
    )
  }

  private fun publishTelemetry(
    prisonId: String,
    compareResults: CompareBookingsResult,
  ) {
    val telemetryName =
      "activity-allocation-reconciliation-report-${if (compareResults.noDifferences()) "success" else "failed"}"
    val telemetryMap = mutableMapOf("prison" to prisonId)

    if (compareResults.nomisOnly.isNotEmpty()) telemetryMap["NOMIS_only"] = compareResults.nomisOnly.toString()
    if (compareResults.dpsOnly.isNotEmpty()) telemetryMap["DPS_only"] = compareResults.dpsOnly.toString()
    if (compareResults.differentCount.isNotEmpty()) telemetryMap["different_count"] = compareResults.differentCount.toString()

    telemetryClient.trackEvent(telemetryName, telemetryMap.toMap())
  }
}

private data class CompareBookingsResult(
  val nomisOnly: List<Long>,
  val dpsOnly: List<Long>,
  val differentCount: List<Long>,
) {
  fun noDifferences() = nomisOnly.isEmpty() && dpsOnly.isEmpty() && differentCount.isEmpty()
}

private data class BookingCounts(val id: Long, val count: Long)
