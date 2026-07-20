package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class StaffReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: StaffNomisApiService,
  private val dpsApiService: StaffDpsApiService,
  private val mappingService: StaffMappingService,
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

    runCatching { generateStaffReconciliationReport() }
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
    .sortedBy { it.nomisStaffId }.take(10).let { mismatch -> "nomisStaffIds" to mismatch.map { it.nomisStaffId }.joinToString() }

  suspend fun generateStaffReconciliationReport(): ReconciliationResult<MismatchStaff> {
    checkTotalsMatch()

    return generateReconciliationReport(
      threadCount = pageSize,
      checkMatch = ::checkStaffMatch,
      nextPage = ::getNextNomisStaffIdsForPage,
    )
  }

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
    .map { page -> ReconciliationSuccessPageResult(ids = page.ids.map { it.staffId }, last = page.ids.last().staffId) }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from staff: $lastNomisStaffId, with $pageSize staff") }

  suspend fun checkStaffMatch(nomisStaffId: Long): MismatchStaff? = runCatching {
    val (nomisStaff, dpsResult: DpsStaffResult) = nomisStaffToPossibleDpsStaff(nomisStaffId)

    return when (dpsResult) {
      is NoMapping -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_STAFF_PREFIX-mismatch",
          mapOf(
            "nomisStaffId" to nomisStaffId.toString(),
            "reason" to "staff-mapping-missing",
          ),
        )
        MismatchStaff(nomisStaffId = nomisStaffId, reason = "staff-mapping-missing")
      }

      is NoStaff -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_STAFF_PREFIX-mismatch",
          mapOf(
            "nomisStaffId" to nomisStaffId.toString(),
            "dpsStaffId" to dpsResult.dpsStaffId,
            "reason" to "dps-record-missing",
          ),
        )
        MismatchStaff(nomisStaffId = nomisStaffId, dpsStaffId = dpsResult.dpsStaffId, reason = "dps-record-missing")
      }

      is Staff -> {
        checkStaffMatch(
          nomisStaffId = nomisStaffId,
          dpsStaffId = dpsResult.staff.user.id,
          dpsStaff = dpsResult.staff.toStaff(),
          nomisStaff = nomisStaff.toStaff(),
        )?.also {
          telemetryClient.trackEvent(
            "$TELEMETRY_STAFF_PREFIX-mismatch",
            mapOf(
              "nomisStaffId" to nomisStaffId.toString(),
              "dpsStaffId" to dpsResult.staff.user.id,
              "reason" to "different-staff-details",
            ),
          )
        }
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
        val mapping = mappingService.getStaffByNomisIdOrNull(nomisStaffId)
        if (mapping != null) {
          val dpsStaff = dpsApiService.getStaffOrNull(mapping.dpsId.toLong())
          if (dpsStaff == null) {
            NoStaff(mapping.dpsId)
          } else {
            Staff(dpsStaff)
          }
        } else {
          NoMapping()
        }
      }
  }.awaitBoth()

  private suspend fun checkTotalsMatch() = runCatching {
    val (nomisTotal, dpsTotal) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getStaffIds().page!!.totalElements } to
        async { dpsApiService.getStaffIds().page!!.totalElements!! }
    }.awaitBoth()

    if (nomisTotal != dpsTotal) {
      telemetryClient.trackEvent(
        "$TELEMETRY_STAFF_PREFIX-mismatch-totals",
        mapOf(
          "nomisTotal" to nomisTotal.toString(),
          "dpsTotal" to dpsTotal.toString(),
        ),
      )
    }
  }.onFailure {
    log.error("Unable to get staff totals", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_STAFF_PREFIX-mismatch-totals-error",
      mapOf(),
    )
  }

  // TODO Additional code to have more specific reconciliation messages
  private fun checkStaffMatch(nomisStaffId: Long, dpsStaffId: String, dpsStaff: StaffSummary, nomisStaff: StaffSummary): MismatchStaff? = takeIf { dpsStaff != nomisStaff }?.let {
    MismatchStaff(
      nomisStaffId = nomisStaffId,
      dpsStaffId = dpsStaffId,
      reason = "different-staff-details",
      nomisStaff = nomisStaff,
      dpsStaff = dpsStaff,
    )
  }
}

data class MismatchStaff(
  val nomisStaffId: Long,
  val dpsStaffId: String? = null,
  val reason: String,
  val nomisStaff: StaffSummary? = null,
  val dpsStaff: StaffSummary? = null,
)

sealed interface DpsStaffResult
data class Staff(val staff: DpsStaffDetails) : DpsStaffResult
class NoMapping : DpsStaffResult
data class NoStaff(val dpsStaffId: String) : DpsStaffResult
