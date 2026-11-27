package uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationNames
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@Service
class CalculateReleaseDatesReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisPrisonerApi: NomisApiService,
  @param:Value($$"${reports.calculate-release-dates.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.calculate-release-dates.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  private val names = ReconciliationNames("calculate-release-dates-reconciliation", "Calculate release dates")

  suspend fun generateReport(selection: PrisonerSelection) = PrisonerReconciliationReport(telemetryClient, nomisPrisonerApi, names, selection, pageSize, threadCount) { checkPrisonersMatch(it) }.run()

  // TODO compare NOMIS and DPS sentence calculations
  @Suppress("UNUSED_PARAMETER")
  suspend fun checkPrisonersMatch(offenderNo: String, suppressTelemetry: Boolean = false): List<MismatchedSentenceCalculation> = emptyList()
}

data class MismatchedSentenceCalculation(
  val offenderNo: String,
  val nomisCalculationId: Long?,
  val dpsCalculationId: Long?,
  val type: String,
)
