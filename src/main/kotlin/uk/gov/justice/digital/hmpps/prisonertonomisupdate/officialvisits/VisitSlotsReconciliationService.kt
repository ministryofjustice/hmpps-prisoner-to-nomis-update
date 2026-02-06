package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class VisitSlotsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: VisitSlotsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_VISIT_SLOTS_PREFIX = "visit-slots-reconciliation"
  }

  suspend fun generateVisitSlotsReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_VISIT_SLOTS_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateReconciliationReport() }
      .onSuccess {
        log.info("Visit slots reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_VISIT_SLOTS_PREFIX-report",
          mapOf(
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
            "prisons-checked" to it.itemsChecked.toString(),
            "mismatches" to it.mismatches.isNotEmpty().toString(),
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_VISIT_SLOTS_PREFIX-report", mapOf("success" to "false"))
        log.error("Visit slots reconciliation report failed", it)
      }
  }

  private fun List<MismatchTimeSlot>.asMap(): Pair<String, String> = this
    .sortedBy { it.prisonId }.take(10).let { mismatch -> "prisonId" to mismatch.joinToString { it.prisonId } }

  suspend fun generateReconciliationReport(): ReconciliationResult<MismatchTimeSlot> {
    val prisonIds = nomisApiService.getActivePrisonsWithTimeSlots()
    val mismatches: List<MismatchTimeSlot> = prisonIds.prisons.flatMap { prison ->
      checkPrisonForMismatches(prisonId = prison.prisonId)
    }
    return ReconciliationResult(
      mismatches = mismatches,
      itemsChecked = prisonIds.prisons.size,
      pagesChecked = 1,
    )
  }

  suspend fun checkPrisonForMismatches(prisonId: String): List<MismatchTimeSlot> {
    val (nomisTimeSlots, dpsTimeSlots) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getTimeSlotsForPrison(prisonId) } to
        async { dpsApiService.getTimeSlotsForPrison(prisonId) }
    }.awaitBoth()

    log.info("Checking prison $prisonId for mismatches NOMIS count ${nomisTimeSlots.timeSlots.size} DPS count ${dpsTimeSlots.timeSlots.size}")

    return emptyList()
  }
}

data class MismatchTimeSlot(
  val prisonId: String,
  val dayOfWeek: String,
  val reason: String,
)
