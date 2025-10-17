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
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.dpsFinanceServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerSubAccountDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RootOffenderIdsWithLast

private const val OFFENDER_NO = "A5678BZ"
private const val OFFENDER_ID = 123456789000L

class PrisonerBalanceReconciliationResourceIntTest(
  @Autowired private val prisonerBalanceReconciliationService: PrisonerBalanceReconciliationService,
) : IntegrationTestBase() {

  @Autowired
  private lateinit var nomisApi: FinanceNomisApiMockServer

  @DisplayName("Prisoner balances reconciliation report")
  @Nested
  inner class GeneratePrisonerBalancesReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetPrisonerBalanceIdentifiersFromId(
        response = RootOffenderIdsWithLast(
          lastOffenderId = 3,
          rootOffenderIds = (1..3L).toList(),
        ),
      )

      stubBalances(
        1,
        nomisPrisonerBalanceResponse().copy(prisonNumber = "A0001NN"),
        dpsPrisonerAccountResponse(),
      )
      stubBalances(
        2,
        nomisPrisonerBalanceResponse().copy(prisonNumber = "A0002NN"),
        dpsPrisonerAccountResponse(),
      )
      stubBalances(
        3,
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
        },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output a mismatch when there is a difference in the  DPS record`() = runTest {
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

  @Nested
  inner class ManualPrisonerBalancesReconciliationReport {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_ID")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_ID")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_ID")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setup() {
        nomisApi.stubGetPrisonBalance()
        dpsFinanceServer.stubGetPrisonBalance()
      }

      @Test
      fun `will output a mismatch when there is a difference in the DPS record`() = runTest {
        stubBalances(
          OFFENDER_ID,
          nomisPrisonerBalanceResponse().copy(accounts = emptyList()),
          dpsPrisonerAccountResponse(),
        )

        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_ID")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("nomis.prisonNumber").isEqualTo(OFFENDER_NO)
          .jsonPath("dps.prisonNumber").isEqualTo(OFFENDER_NO)
          .jsonPath("dps.accounts[0].accountCode").isEqualTo(1001)
          .jsonPath("differences[0].property").isEqualTo("prisoner-balances.accounts")
          .jsonPath("differences[0].dps").isEqualTo(1)
          .jsonPath("differences[0].nomis").isEqualTo(0)

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return no differences when there is a match`() = runTest {
        stubBalances(
          OFFENDER_ID,
          nomisPrisonerBalanceResponse(),
          dpsPrisonerAccountResponse(),
        )

        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_ID")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }
    }
  }

  private fun stubBalances(offenderId: Long, nomisResponse: PrisonerBalanceDto, dpsResponse: PrisonerSubAccountDetailsList) {
    nomisApi.stubGetPrisonerAccountDetails(offenderId, nomisResponse)
    dpsFinanceServer.stubListPrisonerAccounts(nomisResponse.prisonNumber, dpsResponse)
  }
}
