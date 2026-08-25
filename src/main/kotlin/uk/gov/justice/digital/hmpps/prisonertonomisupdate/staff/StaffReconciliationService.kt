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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
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
      generateReconciliationReport(
        threadCount = pageSize,
        checkMatch = ::checkStaffMatch,
        nextPage = ::getNextNomisStaffIdsForPage,
      )
    }
      .onSuccess {
        log.info("Staff reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_STAFF_PREFIX-report",
          mapOf(
            "staff-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_STAFF_PREFIX-report", mapOf("success" to "false"))
        log.error("Staff reconciliation report failed", it)
      }
  }

  private fun List<MismatchStaff>.asMap(): Pair<String, String> = this
    .sortedBy { it.nomisStaffId }.take(10)
    .let { mismatch -> "nomisStaffIds" to mismatch.map { it.nomisStaffId }.joinToString() }

  private suspend fun getNextNomisStaffIdsForPage(lastNomisStaffId: Long): ReconciliationPageResult<Long> = runCatching {
    nomisApiService.getStaffIdsFromId(lastStaffId = lastNomisStaffId, pageSize = pageSize.toLong())
  }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_STAFF_PREFIX-mismatch-page-error",
        mapOf(
          "nomisStaffId" to lastNomisStaffId.toString(),
        ),
      )
      log.error("Unable to match entire page of staff from nomisStaffId: $lastNomisStaffId", it)
    }
    .map { page ->
      ReconciliationSuccessPageResult(
        ids = page.ids.map { it.staffId },
        last = page.ids.last().staffId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from staff: $lastNomisStaffId, with $pageSize staff") }

  suspend fun checkStaffMatch(nomisStaffId: Long): MismatchStaff? = runCatching {
    val (nomisStaff, dpsResult: DpsStaffResult) = nomisStaffToPossibleDpsStaff(nomisStaffId)

    return when (dpsResult) {
      is NoStaff -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_STAFF_PREFIX-mismatch",
          mapOf(
            "nomisStaffId" to nomisStaffId.toString(),
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

data class MismatchStaff(
  val nomisStaffId: Long,
  val dpsStaffId: String? = null,
  val differences: Map<String, String>,

)

sealed interface DpsStaffResult
data class Staff(val staff: PrisonUserReconciliationResponse) : DpsStaffResult
data class NoStaff(val dpsStaffId: String) : DpsStaffResult
