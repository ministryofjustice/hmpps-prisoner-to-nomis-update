package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationNames
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime

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

  private val names = ReconciliationNames(TELEMETRY_PREFIX, "Bed assignment")

  suspend fun manualCheckBedAssignments(offenderNo: String): MismatchBedAssignments? = checkBedAssignments(offenderNo)

  suspend fun generateReconciliationReport(selection: PrisonerSelection) = PrisonerReconciliationReport(telemetryClient, nomisApiService, names, selection, pageSize, threadCount) {
    checkBedAssignments(it)?.differences ?: emptyList()
  }.run()

  internal suspend fun checkBedAssignments(offenderNo: String): MismatchBedAssignments? {
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
      return null
    }
    log.info("Bed assignment differences for offenderNo=$offenderNo: $differences")
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-mismatch",
      mapOf("prisoner" to offenderNo) + differences.associate { it.property to it.toString() },
    )
    return MismatchBedAssignments(nomis = nomisFields, dps = dpsFields, differences = differences)
  }
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
