package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class OfficialVisitsAllReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val mappingService: OfficialVisitsMappingService,
  @param:Value($$"${reports.official-visits.all-visits.reconciliation.page-size}") private val pageSize: Int = 30,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_ALL_VISITS_PREFIX = "official-visits-all-reconciliation"
  }

  suspend fun generateAllVisitsReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_VISITS_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateAllVisitsReconciliationReport() }
      .onSuccess {
        log.info("Official visits all reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_VISITS_PREFIX-report",
          mapOf(
            "visit-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_ALL_VISITS_PREFIX-report", mapOf("success" to "false"))
        log.error("Official visits all reconciliation report failed", it)
      }
  }

  private fun List<MismatchVisit>.asMap(): Pair<String, String> = this
    .sortedBy { it.nomisVisitId }.take(10).let { mismatch -> "nomisVisitIds" to mismatch.map { it.nomisVisitId }.joinToString() }

  suspend fun generateAllVisitsReconciliationReport(): ReconciliationResult<MismatchVisit> {
    checkTotalsMatch()

    return generateReconciliationReport(
      threadCount = pageSize,
      checkMatch = ::checkVisitsMatch,
      nextPage = ::getNextNomisVisitIdsForPage,
    )
  }

  private suspend fun getNextNomisVisitIdsForPage(lastNomisVisitId: Long): ReconciliationPageResult<Long> = runCatching {
    nomisApiService.getOfficialVisitIdsByLastId(lastVisitId = lastNomisVisitId, pageSize = pageSize.toLong())
  }
    .onFailure {
      telemetryClient.trackEvent(
        "$TELEMETRY_ALL_VISITS_PREFIX-mismatch-page-error",
        mapOf(
          "nomisVisitId" to lastNomisVisitId.toString(),
        ),
      )
      log.error("Unable to match entire page of visits from nomisVisitId: $lastNomisVisitId", it)
    }
    .map { page -> ReconciliationSuccessPageResult(ids = page.ids.map { it.visitId }, last = page.ids.last().visitId) }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from visit: $lastNomisVisitId, with $pageSize visits") }

  suspend fun checkVisitsMatch(nomisVisitId: Long): MismatchVisit? = runCatching {
    val (nomisVisit, dpsResult: DpsOfficialVisitResult) = nomisVisitToPossibleDpsVisit(nomisVisitId)

    return when (dpsResult) {
      is NoMapping -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_VISITS_PREFIX-mismatch",
          mapOf(
            "nomisVisitId" to nomisVisitId.toString(),
            "offenderNo" to nomisVisit.offenderNo,
            "reason" to "official-visit-mapping-missing",
          ),
        )
        MismatchVisit(nomisVisitId = nomisVisitId)
      }

      is NoOfficialVisit -> {
        telemetryClient.trackEvent(
          "$TELEMETRY_ALL_VISITS_PREFIX-mismatch",
          mapOf(
            "nomisVisitId" to nomisVisitId.toString(),
            "dpsVisitId" to dpsResult.dpsVisitId,
            "offenderNo" to nomisVisit.offenderNo,
            "reason" to "dps-record-missing",
          ),
        )
        MismatchVisit(nomisVisitId = nomisVisitId)
      }

      is OfficialVisit -> {
        checkVisitsMatch(
          nomisVisitId = nomisVisitId,
          dpsVisit = dpsResult.visit.toVisit(),
          nomisVisit = nomisVisit.toVisit(),
        )?.also {
          telemetryClient.trackEvent(
            "$TELEMETRY_ALL_VISITS_PREFIX-mismatch",
            mapOf(
              "nomisVisitId" to nomisVisitId.toString(),
              "dpsVisitId" to dpsResult.visit.officialVisitId,
              "offenderNo" to nomisVisit.offenderNo,
              "reason" to "different-visit-details",
            ),
          )
        }
      }
    }
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_VISITS_PREFIX-mismatch-error",
      mapOf(
        "nomisVisitId" to "$nomisVisitId",
      ),
    )
  }.getOrNull()

  private suspend fun nomisVisitToPossibleDpsVisit(nomisVisitId: Long): Pair<OfficialVisitResponse, DpsOfficialVisitResult> = withContext(Dispatchers.Unconfined) {
    async { nomisApiService.getOfficialVisit(nomisVisitId) } to
      async {
        val mapping = mappingService.getByNomisIdsOrNull(nomisVisitId)
        if (mapping != null) {
          val dpsOfficialVisit = dpsApiService.getOfficialVisitOrNull(mapping.dpsId.toLong())
          if (dpsOfficialVisit == null) {
            NoOfficialVisit(mapping.dpsId)
          } else {
            OfficialVisit(dpsOfficialVisit)
          }
        } else {
          NoMapping()
        }
      }
  }.awaitBoth()

  private suspend fun checkTotalsMatch() = runCatching {
    val (nomisTotal, dpsTotal) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getOfficialVisitIds().page!!.totalElements } to
        async { dpsApiService.getOfficialVisitIds().page!!.totalElements!! }
    }.awaitBoth()

    if (nomisTotal != dpsTotal) {
      telemetryClient.trackEvent(
        "$TELEMETRY_ALL_VISITS_PREFIX-mismatch-totals",
        mapOf(
          "nomisTotal" to nomisTotal.toString(),
          "dpsTotal" to dpsTotal.toString(),
        ),
      )
    }
  }.onFailure {
    log.error("Unable to get official visit totals", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_ALL_VISITS_PREFIX-mismatch-totals-error",
      mapOf(),
    )
  }

  private fun checkVisitsMatch(nomisVisitId: Long, dpsVisit: Visit, nomisVisit: Visit): MismatchVisit? = takeIf { dpsVisit != nomisVisit }?.let { MismatchVisit(nomisVisitId = nomisVisitId) }
}

data class MismatchVisit(
  val nomisVisitId: Long,
)

private data class Visit(
  val startDateTime: LocalDateTime,
  val endDateTime: LocalDateTime,
  val prisonId: String,
  val visitStatus: VisitStatusType,
  val visitOutcome: VisitCompletionType?,
  val visitors: List<Visitor>,
)
private data class Visitor(val nomisPersonAndDpsContactId: Long, val attendance: AttendanceType?)
sealed interface DpsOfficialVisitResult
data class OfficialVisit(val visit: SyncOfficialVisit) : DpsOfficialVisitResult
class NoMapping : DpsOfficialVisitResult
data class NoOfficialVisit(val dpsVisitId: String) : DpsOfficialVisitResult

private fun OfficialVisitResponse.toVisit() = Visit(
  startDateTime = this.startDateTime,
  endDateTime = this.endDateTime,
  prisonId = this.prisonId,
  visitStatus = this.visitStatus.code.toDpsVisitStatusType(),
  visitOutcome = this.visitOutcome?.code.toDpsVisitCompletionType(this.visitStatus.code),
  visitors = this.visitors.map {
    Visitor(
      nomisPersonAndDpsContactId = it.personId,
      attendance = it.visitorAttendanceOutcome?.code?.toDpsAttendanceType(),
    )
  }.sortedBy { it.nomisPersonAndDpsContactId },
)
private fun SyncOfficialVisit.toVisit() = Visit(
  startDateTime = this.visitDate.atTime(this.startTime.asTime()),
  endDateTime = this.visitDate.atTime(this.endTime.asTime()),
  prisonId = this.prisonCode,
  visitStatus = this.statusCode,
  visitOutcome = this.completionCode,
  visitors = this.visitors.filter { it.contactId != null }.map {
    Visitor(
      nomisPersonAndDpsContactId = it.contactId!!,
      attendance = it.attendanceCode,
    )
  }.sortedBy { it.nomisPersonAndDpsContactId },
)

private fun String.asTime() = LocalTime.parse(this)

private fun String.toDpsVisitStatusType(): VisitStatusType = when (this) {
  "CANC" -> VisitStatusType.CANCELLED
  "VDE" -> VisitStatusType.COMPLETED
  "HMPOP" -> VisitStatusType.COMPLETED
  "OFFEND" -> VisitStatusType.COMPLETED
  "VISITOR" -> VisitStatusType.COMPLETED
  "NORM" -> VisitStatusType.COMPLETED
  "SCH" -> VisitStatusType.SCHEDULED
  "EXP" -> VisitStatusType.EXPIRED
  else -> throw IllegalArgumentException("Unknown visit status code: $this")
}

private fun String?.toDpsVisitCompletionType(nomisVisitStatus: String): VisitCompletionType? = when (this) {
  "NO_VO" -> VisitCompletionType.STAFF_CANCELLED

  "VO_CANCEL" -> VisitCompletionType.STAFF_CANCELLED

  "REFUSED" -> VisitCompletionType.PRISONER_REFUSED

  "OFFCANC" -> VisitCompletionType.PRISONER_CANCELLED

  "VISCANC" -> VisitCompletionType.VISITOR_CANCELLED

  "NSHOW" -> VisitCompletionType.VISITOR_NO_SHOW

  "ADMIN" -> VisitCompletionType.STAFF_CANCELLED

  "ADMIN_CANCEL" -> VisitCompletionType.STAFF_CANCELLED

  "HMP" -> VisitCompletionType.STAFF_CANCELLED

  "NO_ID" -> VisitCompletionType.VISITOR_DENIED

  "BATCH_CANC" -> VisitCompletionType.STAFF_CANCELLED

  null -> when (nomisVisitStatus) {
    "VDE" -> VisitCompletionType.VISITOR_DENIED
    "HMPOP" -> VisitCompletionType.STAFF_EARLY
    "OFFEND" -> VisitCompletionType.PRISONER_EARLY
    "VISITOR" -> VisitCompletionType.VISITOR_EARLY
    "NORM" -> VisitCompletionType.NORMAL
    "SCH" -> null
    "EXP" -> VisitCompletionType.NORMAL
    else -> null
  }

  else -> null
}

private fun String.toDpsAttendanceType(): AttendanceType = when (this) {
  "ATT" -> AttendanceType.ATTENDED
  "ABS" -> AttendanceType.ABSENT
  else -> throw IllegalArgumentException("Unknown attendance type code: $this")
}
