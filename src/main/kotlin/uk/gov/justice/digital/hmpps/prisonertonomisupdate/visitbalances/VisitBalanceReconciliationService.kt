package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection.ACTIVE_PRISONERS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationNames
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto

@Service
class VisitBalanceReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsVisitBalanceApiService: VisitBalanceDpsApiService,
  private val nomisVisitBalanceApiService: VisitBalanceNomisApiService,
  private val nomisApiService: NomisApiService,
  @param:Value($$"${reports.visit-balance.reconciliation.page-size}") private val pageSize: Int = 40,
  @param:Value($$"${reports.visit-balance.reconciliation.thread-count}") private val threadCount: Int = 40,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val names = ReconciliationNames("visitbalance-reports-reconciliation", "Visit balance")

  suspend fun generateReconciliationReport() = PrisonerReconciliationReport(
    telemetryClient,
    nomisApiService,
    names,
    ACTIVE_PRISONERS,
    pageSize,
    threadCount,
    mismatchSummary = { prisoners -> prisoners.sortedBy { it.offenderNo }.take(10).associate { it.offenderNo to it.mismatches.single().summary() } },
  ) { listOfNotNull(checkVisitBalanceMatch(it)) }.run()

  private fun MismatchVisitBalance.summary() = "dpsBalance=$dpsVisitBalance, nomisBalance=$nomisVisitBalance"

  suspend fun checkVisitBalanceMatch(offenderNo: String): MismatchVisitBalance? {
    // Note that the lack of a balance is treated identically to a balance of 0, since it could be created and then not
    // used in either system
    val nomisVisitBalance = nomisVisitBalanceApiService.getVisitBalance(offenderNo)?.toBalance() ?: PrisonerBalance()
    val dpsVisitBalance = dpsVisitBalanceApiService.getVisitBalance(offenderNo)?.toBalance() ?: PrisonerBalance()

    return if (nomisVisitBalance != dpsVisitBalance) {
      MismatchVisitBalance(prisonNumber = offenderNo, dpsVisitBalance = dpsVisitBalance, nomisVisitBalance = nomisVisitBalance).also { mismatch ->
        log.info("VisitBalance mismatch found {}", mismatch)
        telemetryClient.trackEvent(
          "${names.telemetryPrefix}-mismatch",
          mapOf(
            "prisonNumber" to mismatch.prisonNumber,
            "nomisVisitBalance" to mismatch.nomisVisitBalance.visitBalance.toString(),
            "dpsVisitBalance" to mismatch.dpsVisitBalance.visitBalance.toString(),
            "nomisPrivilegedVisitBalance" to mismatch.nomisVisitBalance.privilegedVisitBalance.toString(),
            "dpsPrivilegedVisitBalance" to mismatch.dpsVisitBalance.privilegedVisitBalance.toString(),
          ),
        )
      }
    } else {
      null
    }
  }
}

fun VisitBalanceResponse.toBalance() = PrisonerBalance(visitBalance = remainingVisitOrders, privilegedVisitBalance = remainingPrivilegedVisitOrders)
fun PrisonerBalanceDto.toBalance() = PrisonerBalance(visitBalance = voBalance, privilegedVisitBalance = pvoBalance)

data class MismatchVisitBalance(
  val prisonNumber: String,
  val dpsVisitBalance: PrisonerBalance,
  val nomisVisitBalance: PrisonerBalance,
)

data class PrisonerBalance(
  val visitBalance: Int = 0,
  val privilegedVisitBalance: Int = 0,
)
