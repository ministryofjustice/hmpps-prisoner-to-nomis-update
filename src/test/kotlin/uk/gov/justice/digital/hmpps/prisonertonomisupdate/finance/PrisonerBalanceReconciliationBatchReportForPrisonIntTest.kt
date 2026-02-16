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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RootOffenderIdsWithLast

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
      financeNomisApi.stubGetPrisonerBalanceIdentifiersFromId(
        RootOffenderIdsWithLast(
          rootOffenderIds = listOf(10000, 10001, 10002),
          lastOffenderId = 10002L,
        ),
      )
      stubBalances(
        10000L,
        nomisPrisonerBalanceResponse().copy(prisonNumber = "A0001NN"),
        dpsPrisonerAccountResponse(),
      )
      stubBalances(
        10001L,
        nomisPrisonerBalanceResponse().copy(prisonNumber = "A0002NN"),
        dpsPrisonerAccountResponse(),
      )
      stubBalances(
        10002L,
        nomisPrisonerBalanceResponse().copy(prisonNumber = "A0003NN"),
        dpsPrisonerAccountResponse(),
      )
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      prisonerBalanceReconciliationService.generatePrisonerBalanceReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("prisoner-balance-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report telemetry`() = runTest {
      prisonerBalanceReconciliationService.generatePrisonerBalanceReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("prisoner-balance-reconciliation-report"),
        check {
          assertThat(it).containsEntry("balance-count", "3")
          assertThat(it).containsEntry("page-count", "1")
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
        nomisPrisonerBalanceResponse().copy(prisonNumber = "A0002NN"),
        dpsPrisonerAccountResponse().copy(items = emptyList()),
      )
      prisonerBalanceReconciliationService.generatePrisonerBalanceReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("prisoner-balance-reconciliation-report"),
        check {
          assertThat(it).containsEntry("balance-count", "3")
          assertThat(it).containsEntry("page-count", "1")
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
          eq("prisoner-balance-reconciliation-report"),
          any(),
          isNull(),
        )
      }
    }
  }

  private fun stubBalances(offenderId: Long, nomisResponse: PrisonerBalanceDto, dpsResponse: PrisonerEstablishmentBalanceDetailsList) {
    financeNomisApi.stubGetPrisonerAccountDetails(offenderId, nomisResponse)
    dpsFinanceServer.stubListPrisonerAccounts(nomisResponse.prisonNumber, dpsResponse)
  }
}
