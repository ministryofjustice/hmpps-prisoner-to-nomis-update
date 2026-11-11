package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateRangesReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse
import kotlin.collections.set

@Service
class StaffReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: StaffNomisApiService,
  private val dpsApiService: StaffDpsApiService,
  @param:Value($$"${reports.staff.reconciliation.page-size}") private val pageSize: Int = 30,
  @param:Value($$"${reports.staff.reconciliation.thread-count:10}") private val threadCount: Int = 10,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_STAFF_PREFIX = "staff-reconciliation"
  }

  suspend fun generateReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_STAFF_PREFIX-requested",
      mapOf(),
    )

    runCatching {
      generateRangesReconciliationReport(
        threadCount = threadCount,
        checkMatch = ::checkStaffMatch,
        idRanges = { nomisApiService.getAllStaffIdRanges(pageSize.toLong()) },
        idsInRange = { range -> this.getAllStaffIdsInRange(range.fromId, range.toId) },
      )
    }
      .onSuccess {
        log.info("Staff reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_STAFF_PREFIX-report",
          mapOf(
            "staff-count" to it.itemsChecked,
            "pages-count" to it.pagesChecked,
            "mismatch-count" to it.mismatches.size,
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_STAFF_PREFIX-report", mapOf("success" to "false"))
        log.error("Staff reconciliation report failed", it)
      }
  }

  suspend fun checkStaffMatch(nomisStaffId: Long): MismatchStaff? = runCatching {
    val (nomisStaff, dpsResult: DpsStaffResult) = nomisStaffToPossibleDpsStaff(nomisStaffId)

    return when (dpsResult) {
      is NoStaff -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_STAFF_PREFIX-mismatch",
          mapOf(
            "nomisStaffId" to nomisStaffId,
            "reason" to "dps-record-missing",
          ),
        )
        MismatchStaff(
          nomisStaffId = nomisStaffId,
          dpsStaffId = null,
          differences = mapOf("dps-record-missing" to "true"),
        )
      }

      is Staff -> {
        findDifferences(
          nomisStaffId = nomisStaffId,
          dpsStaffId = dpsResult.staff.userId.toString(),
          dpsStaff = dpsResult.staff.toStaff(),
          nomisStaff = nomisStaff.toStaff(),
        )
      }
    }
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_STAFF_PREFIX-mismatch-error",
      mapOf(
        "nomisStaffId" to "$nomisStaffId",
      ),
    )
  }.getOrNull()

  internal suspend fun getAllStaffIdsInRange(
    fromStaffId: Long,
    toStaffId: Long,
  ): ReconciliationPageResult<Long> = runCatching {
    nomisApiService.getAllStaffIdsInRange(
      fromStaffId = fromStaffId,
      toStaffId = toStaffId,
    )
  }.fold(
    onSuccess = { ids ->
      ReconciliationSuccessPageResult(ids = ids, last = 0)
        .also { log.info("Page requested from fromStaffId: $fromStaffId, toStaffId: $toStaffId, with ${it.ids.size} staff") }
    },
    onFailure = {
      telemetryClient.trackEvent(
        "$TELEMETRY_STAFF_PREFIX-mismatch-page-error",
        mapOf(
          "fromStaffId" to fromStaffId,
          "toStaffId" to toStaffId,
          "error" to (it.message ?: it.javaClass.name),
        ),
      )
      log.error("Unable to match entire page of staff from fromStaffId: $fromStaffId, toStaffId: $toStaffId", it)
      ReconciliationErrorPageResult(it)
    },
  )

  private suspend fun nomisStaffToPossibleDpsStaff(nomisStaffId: Long): Pair<StaffDetails, DpsStaffResult> = withContext(Dispatchers.Unconfined) {
    async { nomisApiService.getStaffDetails(nomisStaffId) } to
      async {
        val dpsStaff = dpsApiService.getStaffOrNull(nomisStaffId)
        if (dpsStaff == null) {
          NoStaff(nomisStaffId.toString())
        } else {
          Staff(dpsStaff)
        }
      }
  }.awaitBoth()

  private fun findDifferences(
    nomisStaffId: Long,
    dpsStaffId: String,
    dpsStaff: StaffSummary,
    nomisStaff: StaffSummary,
  ): MismatchStaff? {
    val differences = mutableMapOf<String, String>()

    appendStaffDifferences(nomis = nomisStaff, dps = dpsStaff, differences = differences)

    return differences.takeIf { it.isNotEmpty() }
      ?.let { MismatchStaff(nomisStaffId = nomisStaffId, dpsStaffId = dpsStaffId, differences = it) }
      ?.also { mismatch ->
        log.info("Staff mismatch found {}", mismatch)
        telemetryClient.trackEvent(
          "$TELEMETRY_STAFF_PREFIX-mismatch",
          (
            telemetryOf(
              "nomisStaffId" to mismatch.nomisStaffId,
            ) + differences.map { it.key to it.value }
            ).toMutableMap().also { telemetry ->
            mismatch.dpsStaffId?.let {
              telemetry["dpsStaffId"] = it
            }
          },
        )
      }
  }
}
private fun List<MismatchStaff>.asMap(): Pair<String, String> = this
  .sortedBy { it.nomisStaffId }.take(10)
  .let { mismatch -> "nomisStaffIds" to mismatch.map { it.nomisStaffId }.joinToString() }

data class MismatchStaff(
  val nomisStaffId: Long,
  val dpsStaffId: String? = null,
  val differences: Map<String, String>,

)

sealed interface DpsStaffResult
data class Staff(val staff: PrisonUserReconciliationResponse) : DpsStaffResult
data class NoStaff(val dpsStaffId: String) : DpsStaffResult
