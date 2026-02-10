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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncTimeSlotSummaryItem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate

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

    val nomisSlotPerDay = nomisTimeSlots.timeSlots.groupBy { it.dayOfWeek.toString() }
    val dpsSlotPerDay = dpsTimeSlots.timeSlots.groupBy { it.timeSlot.dayCode.toString() }

    val mismatches = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").flatMap { dayOfWeek ->
      val nomisSlots = nomisSlotPerDay[dayOfWeek] ?: emptyList()
      val dpsSlots = dpsSlotPerDay[dayOfWeek] ?: emptyList()
      if (nomisSlots.size != dpsSlots.size) {
        listOf(
          MismatchTimeSlot(prisonId = prisonId, dayOfWeek = dayOfWeek, reason = "time-slot-count-mismatch").also {
            telemetryClient.trackEvent(
              "$TELEMETRY_VISIT_SLOTS_PREFIX-mismatch",
              mapOf(
                "prisonId" to prisonId,
                "dayOfWeek" to dayOfWeek,
                "nomisCount" to nomisSlots.size.toString(),
                "dpsCount" to dpsSlots.size.toString(),
                "reason" to "time-slot-count-mismatch",
              ),
            )
          },
        )
      } else {
        checkTimeSlotsAtPrisonOnDay(
          prisonId = prisonId,
          dayOfWeek = dayOfWeek,
          nomisSlots = nomisSlots,
          dpsSlots = dpsSlots,
        )
      }
    }

    return mismatches
  }

  private fun checkTimeSlotsAtPrisonOnDay(
    prisonId: String,
    dayOfWeek: String,
    nomisSlots: List<VisitTimeSlotResponse>,
    dpsSlots: List<SyncTimeSlotSummaryItem>,
  ): List<MismatchTimeSlot> {
    val dpsTimeSlotSummaries = dpsSlots.map { it.toTimeSlotSummary() }
    return nomisSlots.mapNotNull { nomisNomisTimeSlotData ->
      val nomisTimeSlot = nomisNomisTimeSlotData.toTimeSlotSummary()
      val dpsTimeSlot = dpsTimeSlotSummaries.find { it == nomisTimeSlot }
      if (dpsTimeSlot == null) {
        MismatchTimeSlot(prisonId = prisonId, dayOfWeek = dayOfWeek, reason = "matching-time-slot-not-found").also {
          telemetryClient.trackEvent(
            "$TELEMETRY_VISIT_SLOTS_PREFIX-mismatch",
            mapOf(
              "prisonId" to prisonId,
              "dayOfWeek" to dayOfWeek,
              "startTime" to nomisTimeSlot.startTime,
              "endTime" to nomisTimeSlot.endTime,
              "nomisTimeSlotSequence" to nomisNomisTimeSlotData.timeSlotSequence,
              "reason" to "matching-time-slot-not-found",
            ),
          )
        }
      } else {
        null
      }
    }
  }
}

private fun VisitTimeSlotResponse.toTimeSlotSummary() = TimeSlotSummary(
  startTime = this.startTime,
  endTime = this.endTime,
  expiryDate = this.expiryDate,
  startDate = this.effectiveDate,
  visitSlots = this.visitSlots.map {
    VisitSlotSummary(
      maxAdults = it.maxAdults ?: 0,
      maxGroups = it.maxGroups ?: 0,
    )
  }.sortedWith(compareBy<VisitSlotSummary> { it.maxAdults }.thenBy { it.maxGroups }),
)

private fun SyncTimeSlotSummaryItem.toTimeSlotSummary() = TimeSlotSummary(
  startTime = this.timeSlot.startTime,
  endTime = this.timeSlot.endTime,
  expiryDate = this.timeSlot.expiryDate,
  startDate = this.timeSlot.effectiveDate,
  visitSlots = this.visitSlots.map {
    VisitSlotSummary(
      maxAdults = it.maxAdults ?: 0,
      maxGroups = it.maxGroups ?: 0,
    )
  }.sortedWith(compareBy<VisitSlotSummary> { it.maxAdults }.thenBy { it.maxGroups }),
)

data class MismatchTimeSlot(
  val prisonId: String,
  val dayOfWeek: String,
  val reason: String,
)

data class TimeSlotSummary(
  val startTime: String,
  val endTime: String,
  val expiryDate: LocalDate?,
  val startDate: LocalDate,
  val visitSlots: List<VisitSlotSummary>,
)

data class VisitSlotSummary(
  val maxAdults: Int,
  val maxGroups: Int,
)
