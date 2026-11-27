package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateRangesReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime
import kotlin.collections.mutableListOf

@Service
class BedAssignmentReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val bedAssignmentNomisApiService: BedAssignmentNomisApiService,
  private val bedAssignmentDpsApiService: BedAssignmentDpsApiService,
  private val bedAssignmentMappingApiService: BedAssignmentMappingApiService,
  private val nomisApiService: NomisApiService,
  @Value($$"${reports.bedassignment.reconciliation.page-size:1000}") private val pageSize: Int = 1000,
  @Value($$"${reports.bedassignment.reconciliation.thread-count:10}") private val threadCount: Int = 10,
) {
  private companion object {
    private const val TELEMETRY_PREFIX = "bed-assignment-reports-reconciliation"
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun manualCheckBedAssignments(offenderNo: String): MismatchBedAssignments? = checkBedAssignments(offenderNo)

  suspend fun generateReconciliationReportBatch(activeOnly: Boolean) {
    telemetryClient.trackEvent("$TELEMETRY_PREFIX-requested", mapOf("activeOnly" to activeOnly.toString()))
    runCatching {
      generateRangesReconciliationReport(
        threadCount = threadCount,
        checkMatch = ::checkBedAssignments,
        idRanges = { nomisApiService.getAllPrisonersIdRanges(pageSize.toLong(), activeOnly) },
        idsInRange = { getOffenderNosInRange(it.fromId, it.toId, activeOnly) },
      )
    }.onSuccess {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-report",
        mapOf(
          "activeOnly" to activeOnly.toString(),
          "prisoners-count" to it.itemsChecked.toString(),
          "page-count" to it.pagesChecked.toString(),
          "mismatch-count" to it.mismatches.size.toString(),
          "success" to "true",
        ),
      )
    }.onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-report",
        mapOf("success" to "false", "error" to (it.message ?: it.javaClass.name)),
      )
      log.error("Bed assignment reconciliation report failed", it)
    }
  }

  internal suspend fun checkBedAssignments(offenderNo: String): MismatchBedAssignments? = runCatching {
    val nomisAssignments = bedAssignmentNomisApiService.getBedAssignmentsForPrisoner(offenderNo).bedAssignments
    val dpsAssignments = bedAssignmentDpsApiService.getBedAssignments(offenderNo)
    val nomisFields = nomisAssignments.map {
      BedAssignmentFields(
        id = "${it.bookingId}-${it.sequence}",
        livingUnit = it.livingUnitDescription,
        startDate = it.assignmentDateTime,
        endDate = it.assignmentEndDateTime,
      )
    }
    val dpsFields = dpsAssignments.map {
      BedAssignmentFields(id = it.id.toString(), livingUnit = it.locationKey, startDate = it.startAt, endDate = it.endAt)
    }
    val unmatchedDps = dpsFields.associateBy { it.id }.toMutableMap()
    val differences = mutableListOf<BedAssignmentDifference>()
    nomisAssignments.zip(nomisFields).forEach { (assignment, nomis) ->
      val mapping = bedAssignmentMappingApiService.getMappingByNomisIdOrNull(assignment.bookingId, assignment.sequence)
      val dps = mapping?.let { unmatchedDps.remove(it.dpsBedAssignmentId) }
      if (dps == null) {
        differences.add(BedAssignmentDifference("bed-assignments[${nomis.id}]", 0, 1, nomisId = nomis.id))
      } else {
        if (dps.livingUnit != nomis.livingUnit) {
          differences.add(BedAssignmentDifference("bed-assignments[${nomis.id}].living-unit", dps.livingUnit, nomis.livingUnit, dps.id, nomis.id))
        }
        if (dps.startDate != nomis.startDate) {
          differences.add(BedAssignmentDifference("bed-assignments[${nomis.id}].start-date", dps.startDate, nomis.startDate, dps.id, nomis.id))
        }
        if (dps.endDate != nomis.endDate) {
          differences.add(BedAssignmentDifference("bed-assignments[${nomis.id}].end-date", dps.endDate, nomis.endDate, dps.id, nomis.id))
        }
      }
    }
    unmatchedDps.values.forEach {
      differences.add(BedAssignmentDifference("bed-assignments[${it.id}]", 1, 0, dpsId = it.id))
    }

    if (differences.isEmpty()) {
      null
    } else {
      log.info("Bed assignment differences for offenderNo=$offenderNo: $differences")
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-mismatch",
        mapOf("prisoner" to offenderNo) + differences.associate { it.property to it.toString() },
      )
      MismatchBedAssignments(nomis = nomisFields, dps = dpsFields, differences = differences)
    }
  }.onFailure {
    log.error("Unable to match bed assignments for offenderNo=$offenderNo", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-mismatch-error",
      mapOf("prisoner" to offenderNo, "error" to (it.message ?: it.javaClass.name)),
    )
  }.getOrNull()

  internal suspend fun getOffenderNosInRange(fromRootOffenderId: Long, toRootOffenderId: Long, activeOnly: Boolean): ReconciliationPageResult<String> = runCatching {
    nomisApiService.getAllPrisonersInRange(
      fromRootOffenderId = fromRootOffenderId,
      toRootOffenderId = toRootOffenderId,
      activeOnly = activeOnly,
    )
  }.fold(
    onSuccess = { prisoners ->
      ReconciliationSuccessPageResult(ids = prisoners.map { it.prisonNumber }, last = 0)
        .also { log.info("Page requested from fromRootOffenderId: $fromRootOffenderId, toRootOffenderId: $toRootOffenderId, with ${it.ids.size} prisoners") }
    },
    onFailure = {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-mismatch-page-error",
        mapOf(
          "fromRootOffenderId" to fromRootOffenderId.toString(),
          "toRootOffenderId" to toRootOffenderId.toString(),
          "error" to (it.message ?: it.javaClass.name),
        ),
      )
      log.error("Unable to match entire page of prisoners from fromRootOffenderId: $fromRootOffenderId, toRootOffenderId: $toRootOffenderId", it)
      ReconciliationErrorPageResult(it)
    },
  )
}

data class MismatchBedAssignments(
  val nomis: List<BedAssignmentFields>,
  val dps: List<BedAssignmentFields>,
  val differences: List<BedAssignmentDifference>,
)

data class BedAssignmentFields(
  val id: String,
  val livingUnit: String?,
  val startDate: LocalDateTime,
  val endDate: LocalDateTime?,
)

data class BedAssignmentDifference(
  val property: String,
  val dps: Any?,
  val nomis: Any?,
  val dpsId: String? = null,
  val nomisId: String? = null,
)
