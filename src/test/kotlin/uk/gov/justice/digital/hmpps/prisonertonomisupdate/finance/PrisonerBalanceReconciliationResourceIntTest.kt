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
import org.springframework.core.ParameterizedTypeReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.dpsFinanceServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RootOffenderIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal

class PrisonerBalanceReconciliationResourceIntTest(
  @Autowired private val prisonerBalanceReconciliationService: PrisonerBalanceReconciliationService,
) : IntegrationTestBase() {

  @Autowired
  private lateinit var financeNomisApi: FinanceNomisApiMockServer

  @DisplayName("Prisoner balances reconciliation report")
  @Nested
  inner class GeneratePrisonerBalancesReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      financeNomisApi.stubGetPrisonerBalanceIdentifiersFromId(
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
          assertThat(it).containsEntry("filter-prison", "")
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
          assertThat(it).containsEntry("filter-prison", "")
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

  @DisplayName("Reconcile one prisoner by offenderId")
  @Nested
  inner class ManualPrisonerBalancesReconciliationReportOffenderId {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/id/$OFFENDER_ID")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/id/$OFFENDER_ID")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/id/$OFFENDER_ID")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setup() {
        financeNomisApi.stubGetPrisonBalance()
        dpsFinanceServer.stubGetPrisonBalance()
      }

      @Test
      fun `will output a mismatch when there is a difference in the DPS record`() = runTest {
        stubBalances(
          OFFENDER_ID,
          nomisPrisonerBalanceResponse().copy(accounts = emptyList()),
          dpsPrisonerAccountResponse(),
        )

        webTestClient.get().uri("/prisoner-balance/reconciliation/id/$OFFENDER_ID")
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

        verify(telemetryClient).trackEvent(
          eq("prisoner-balance-reports-reconciliation-mismatch"),
          check {
            assertThat(it["prisoner"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisoner-balances.accounts"]).isEqualTo("Difference(property=prisoner-balances.accounts, dps=1, nomis=0, id=null)")
          },
          isNull(),
        )
      }

      @Test
      fun `will return no differences when there is a match`() = runTest {
        stubBalances(
          OFFENDER_ID,
          nomisPrisonerBalanceResponse(),
          dpsPrisonerAccountResponse(),
        )

        webTestClient.get().uri("/prisoner-balance/reconciliation/id/$OFFENDER_ID")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }
    }
  }

  @DisplayName("Reconcile one prisoner by offenderNo")
  @Nested
  inner class ManualPrisonerBalancesReconciliationReportOffenderNo {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_NO")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_NO")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_NO")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setup() {
        financeNomisApi.stubGetPrisonBalance()
        nomisApi.stubGetPrisonerDetails(
          OFFENDER_NO,
          PrisonerDetails(
            offenderNo = OFFENDER_NO,
            offenderId = OFFENDER_ID,
            bookingId = 1,
            location = "BXI",
            active = true,
            rootOffenderId = OFFENDER_ID,
          ),
        )
        dpsFinanceServer.stubGetPrisonBalance()
      }

      @Test
      fun `will output a mismatch when there is a difference in the DPS record`() = runTest {
        stubBalances(
          OFFENDER_ID,
          nomisPrisonerBalanceResponse().copy(accounts = emptyList()),
          dpsPrisonerAccountResponse(),
        )

        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_NO")
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
      }

      @Test
      fun `will return no differences when there is a match`() = runTest {
        stubBalances(
          OFFENDER_ID,
          nomisPrisonerBalanceResponse(),
          dpsPrisonerAccountResponse(),
        )

        webTestClient.get().uri("/prisoner-balance/reconciliation/$OFFENDER_NO")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }
    }
  }

  @DisplayName("Reconcile prisoners in a single prison")
  @Nested
  inner class ManualPrisonerBalancesReconciliationReportSinglePrison {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/prison/ASI")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/prison/ASI")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoner-balance/reconciliation/prison/ASI")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setup() {
        financeNomisApi.stubGetPrisonerBalanceIdentifiersFromId(
          RootOffenderIdsWithLast(
            rootOffenderIds = listOf(1, 2, 3),
            lastOffenderId = 3L,
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
      fun `will output a mismatch when there is a difference in the DPS record`() = runTest {
        stubBalances(
          2,
          nomisPrisonerBalanceResponse().copy(prisonNumber = "A0002NN").copy(accounts = emptyList()),
          dpsPrisonerAccountResponse(),
        )

        val result = webTestClient.get()
          .uri("/prisoner-balance/reconciliation/prison/ASI")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody(object : ParameterizedTypeReference<ReconciliationResult<MismatchPrisonerBalance>>() {})
          .returnResult()
          .responseBody!!

        assertThat(result.mismatches).hasSize(1)
        val first = result.mismatches.first()
        assertThat(first.nomis.prisonNumber).isEqualTo("A0002NN")
        assertThat(first.nomis.accounts).isEmpty()
        assertThat(first.dps.prisonNumber).isEqualTo("A0002NN")
        assertThat(first.dps.accounts.first().prisonId).isEqualTo("MDI")
        assertThat(first.dps.accounts.first().balance).isEqualTo(BigDecimal("1.5"))
        assertThat(first.dps.accounts.first().holdBalance).isEqualTo(BigDecimal("0.3"))
        assertThat(first.dps.accounts.first().accountCode).isEqualTo(1001)
        assertThat(first.differences.first().property).isEqualTo("prisoner-balances.accounts")
        assertThat(first.differences.first().nomis).isEqualTo(0)
        assertThat(first.differences.first().dps).isEqualTo(1)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("prisoner-balance-reports-reconciliation-mismatch"),
            check {
              assertThat(it["prisoner"]).isEqualTo("A0002NN")
              assertThat(it["prisoner-balances.accounts"]).isEqualTo("Difference(property=prisoner-balances.accounts, dps=1, nomis=0, id=null)")
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun stubBalances(offenderId: Long, nomisResponse: PrisonerBalanceDto, dpsResponse: PrisonerEstablishmentBalanceDetailsList) {
    financeNomisApi.stubGetPrisonerAccountDetails(offenderId, nomisResponse)
    dpsFinanceServer.stubListPrisonerAccounts(nomisResponse.prisonNumber, dpsResponse)
  }
}
