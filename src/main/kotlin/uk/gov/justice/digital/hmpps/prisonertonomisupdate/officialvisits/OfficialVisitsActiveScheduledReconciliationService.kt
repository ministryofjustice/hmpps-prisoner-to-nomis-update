package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationNames
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate

@Service
class OfficialVisitsActiveScheduledReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val nomisPrisonerApiService: NomisApiService,
  @param:Value($$"${reports.official-visits.active-prisoners-visits.reconciliation.page-size}") private val pageSize: Int = 30,
  @param:Value($$"${reports.official-visits.active-prisoners-visits.reconciliation.thread-count}") private val threadCount: Int = 30,
) {
  private companion object {
    const val TELEMETRY_ACTIVE_VISITS_PREFIX = "official-visits-active-reconciliation"
  }

  private val names = ReconciliationNames(TELEMETRY_ACTIVE_VISITS_PREFIX, "Official visits active scheduled")

  suspend fun generateReport() = PrisonerReconciliationReport(telemetryClient, nomisPrisonerApiService, names, PrisonerSelection.ACTIVE_PRISONERS, pageSize, threadCount) { checkPrisonerVisitsMatch(it) }.run()

  suspend fun checkPrisonerVisitsMatch(offenderNo: String): List<MismatchPrisonerVisit> {
    val (nomisVisits, dpsVisits) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getOfficialVisitsForPrisoner(offenderNo, fromDate = LocalDate.now(), toDate = LocalDate.now().plusMonths(1)) } to
        async { dpsApiService.getOfficialVisitsForPrisoner(offenderNo, fromDate = LocalDate.now(), toDate = LocalDate.now().plusMonths(1)) }
    }.awaitBoth()
    return checkVisitsMatch(
      offenderNo = offenderNo,
      dpsVisits = dpsVisits.map { it.toVisit() },
      nomisVisits = nomisVisits.map { it.toVisit() },
    )
  }

  private fun checkVisitsMatch(offenderNo: String, dpsVisits: List<OfficialVisitSummary>, nomisVisits: List<OfficialVisitSummary>): List<MismatchPrisonerVisit> {
    val mismatches = mutableListOf<MismatchPrisonerVisit>()
    if (dpsVisits.size != nomisVisits.size) {
      mismatches.add(MismatchPrisonerVisit(offenderNo = offenderNo))
      telemetryClient.trackEvent(
        "$TELEMETRY_ACTIVE_VISITS_PREFIX-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "dpsCount" to dpsVisits.size.toString(),
          "nomisCount" to nomisVisits.size.toString(),
          "reason" to "visit-record-missing",
        ),
      )
    }
    dpsVisits.filter { !nomisVisits.contains(it) }.forEach { dpsVisit ->
      mismatches.add(MismatchPrisonerVisit(offenderNo = offenderNo))
      telemetryClient.trackEvent(
        "$TELEMETRY_ACTIVE_VISITS_PREFIX-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "startDateTime" to dpsVisit.startDateTime.toString(),
          "reason" to "dps-visit-not-found",
        ),
      )
    }
    nomisVisits.filter { !dpsVisits.contains(it) }.forEach { nomisVisit ->
      mismatches.add(MismatchPrisonerVisit(offenderNo = offenderNo))
      telemetryClient.trackEvent(
        "$TELEMETRY_ACTIVE_VISITS_PREFIX-mismatch",
        mapOf(
          "offenderNo" to offenderNo,
          "startDateTime" to nomisVisit.startDateTime.toString(),
          "reason" to "nomis-visit-not-found",
        ),
      )
    }

    return mismatches.toList()
  }
}

data class MismatchPrisonerVisit(
  val offenderNo: String,
)
