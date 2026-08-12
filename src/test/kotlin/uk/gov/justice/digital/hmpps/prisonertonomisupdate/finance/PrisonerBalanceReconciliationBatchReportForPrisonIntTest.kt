package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.dpsFinanceServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SubAccountBalanceForReconciliation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAggregatedAccountsDto

@TestPropertySource(
  properties = [
    "reports.prisoner.balance.reconciliation.filter-prison=MDI",
  ],
)
class PrisonerBalanceReconciliationBatchReportForPrisonIntTest(
  @Autowired private val prisonerBalanceReconciliationService: PrisonerBalanceReconciliationService,
) : IntegrationTestBase() {

  @Autowired
  private lateinit var financeNomisApi: FinanceNomisApiMockServer

  @DisplayName("Prisoner balances reconciliation report for prison")
  @Nested
  inner class GeneratePrisonerBalancesReconciliationReportForPrison {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      financeNomisApi.stubGetPrisonerBalanceIdentifierRanges(
        pageSize = 10,
        totalElements = 20,
      )
      financeNomisApi.stubGetPrisonerBalanceIdentifiersInRange(
        fromRootOffenderId = 0,
        toRootOffenderId = 10,
      )
      financeNomisApi.stubGetPrisonerBalanceIdentifiersInRange(
        fromRootOffenderId = 10,
        toRootOffenderId = 20,
      )
      for (i in 1..20) {
        stubBalances(i.toLong(), nomisPrisonerAccounts().copy(prisonNumber = "A000${i}NN"), dpsAccount())
      }
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      prisonerBalanceReconciliationService.generatePrisonerBalanceReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("prisoner-balance-reports-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report telemetry`() = runTest {
      prisonerBalanceReconciliationService.generatePrisonerBalanceReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("prisoner-balance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("balance-count", "20")
          assertThat(it).containsEntry("page-count", "2")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("success", "true")
          assertThat(it).containsEntry("filter-prison", "MDI")
        },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output a mismatch when there is a difference in the DPS record`() = runTest {
      stubBalances(
        2,
        nomisPrisonerAccounts().copy(prisonNumber = "A0002NN"),
        emptyMap(),
      )
      prisonerBalanceReconciliationService.generatePrisonerBalanceReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("prisoner-balance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("balance-count", "20")
          assertThat(it).containsEntry("page-count", "2")
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("success", "true")
          assertThat(it).containsEntry("filter-prison", "MDI")
        },
        isNull(),
      )

      awaitReportFinished()
    }

    private fun awaitReportFinished() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("prisoner-balance-reports-reconciliation-report"),
          any(),
          isNull(),
        )
      }
    }
  }

  private fun stubBalances(offenderId: Long, nomisResponse: PrisonerAggregatedAccountsDto, dpsResponse: Map<String, SubAccountBalanceForReconciliation>) {
    financeNomisApi.stubGetPrisonerAccounts(offenderId, nomisResponse)
    dpsFinanceServer.stubGetPrisonerAccounts(nomisResponse.prisonNumber, dpsResponse)
  }
}
