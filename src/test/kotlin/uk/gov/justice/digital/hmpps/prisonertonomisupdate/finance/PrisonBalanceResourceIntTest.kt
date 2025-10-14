package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import java.math.BigDecimal

class PrisonBalanceResourceIntTest(
  @Autowired private val reconciliationService: PrisonBalanceReconciliationService,
  @Autowired private val nomisApi: FinanceNomisApiMockServer,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>
  private val dpsFinanceServer = FinanceDpsApiExtension.dpsFinanceServer

  @DisplayName("PUT /prison-balance/reports/reconciliation")
  @Nested
  inner class GeneratePrisonBalanceReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      val numberOfPrisons = 20L
      nomisApi.stubGetPrisonBalanceIds() // gets 20 ids MDI1 -> MDI20

      // mock non-matching for first, second and last prisons
      nomisApi.stubGetPrisonBalance(
        prisonBalance = prisonBalanceDto().copy(
          prisonId = "MDI1",
          accountBalances = listOf(
            prisonAccountBalanceDto().copy(balance = BigDecimal.valueOf(7)),
          ),
        ),
      )
      dpsFinanceServer.stubGetPrisonBalance(
        prisonId = "MDI1",
        response = prisonAccounts(),
      )

      nomisApi.stubGetPrisonBalance(
        prisonBalance = prisonBalanceDto().copy(
          prisonId = "MDI2",
          accountBalances = listOf(
            prisonAccountBalanceDto().copy(accountCode = 2102),
          ),
        ),
      )
      dpsFinanceServer.stubGetPrisonBalance(
        prisonId = "MDI2",
        response = prisonAccounts(),
      )

      nomisApi.stubGetPrisonBalance(
        prisonBalance = prisonBalanceDto().copy(
          prisonId = "MDI20",
          accountBalances = listOf(
            prisonAccountBalanceDto().copy(balance = BigDecimal.valueOf(63.4)),
          ),
        ),
      )
      dpsFinanceServer.stubGetPrisonBalance(
        prisonId = "MDI20",
        response = prisonAccounts(),
      )

      // all others are ok
      (3..<numberOfPrisons).forEach {
        val prisonId = generatePrisonId(sequence = it)
        nomisApi.stubGetPrisonBalance(prisonId = prisonId)
        dpsFinanceServer.stubGetPrisonBalance(
          prisonId = prisonId,
          response = prisonAccounts(),
        )
      }
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("prison-balance-reports-reconciliation-requested"),
        check { assertThat(it).containsEntry("prisons", "20") },
        isNull(),
      )
    }

    @Test
    fun `should call to nomis to get the prison ids`() = runTest {
      reconciliationService.generateReconciliationReport()

      awaitReportFinished()

      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/finance/prison/ids")),
      )
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
      reconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("prison-balance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("prison-balance-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      val mismatchedRecords = telemetryCaptor.allValues.map { it["prisonId"] }

      assertThat(mismatchedRecords).containsOnly("MDI1", "MDI2", "MDI20")
      with(telemetryCaptor.allValues.find { it["prisonId"] == "MDI1" }) {
        assertThat(this).containsEntry("dpsAccountCount", "1")
        assertThat(this).containsEntry("nomisAccountCount", "1")
        assertThat(this).containsEntry("reason", "different-prison-account-balance")
        assertThat(this).containsEntry("dpsPrisonBalances", "[AccountSummary(accountCode=2101, balance=23.45)]")
        assertThat(this).containsEntry("nomisPrisonBalances", "[AccountSummary(accountCode=2101, balance=7)]")
      }
      with(telemetryCaptor.allValues.find { it["prisonId"] == "MDI2" }) {
        assertThat(this).containsEntry("dpsAccountCount", "1")
        assertThat(this).containsEntry("nomisAccountCount", "1")
        assertThat(this).containsEntry("reason", "different-account-codes")
        assertThat(this).containsEntry("missingFromNomis", "[2101]")
        assertThat(this).containsEntry("missingFromDps", "[2102]")
      }
      with(telemetryCaptor.allValues.find { it["prisonId"] == "MDI20" }) {
        assertThat(this).containsEntry("dpsAccountCount", "1")
        assertThat(this).containsEntry("nomisAccountCount", "1")
        assertThat(this).containsEntry("reason", "different-prison-account-balance")
        assertThat(this).containsEntry("dpsPrisonBalances", "[AccountSummary(accountCode=2101, balance=23.45)]")
        assertThat(this).containsEntry("nomisPrisonBalances", "[AccountSummary(accountCode=2101, balance=63.4)]")
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      dpsFinanceServer.stubGetPrisonBalance("MDI2", HttpStatus.INTERNAL_SERVER_ERROR)

      reconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(1)).trackEvent(
        eq("prison-balance-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("prison-balance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
        },
        isNull(),
      )
    }

    @Test
    fun `when initial prison count call fails the whole report fails`() = runTest {
      nomisApi.stubGetPrisonBalanceIdsWithError(responseCode = 500)

      assertThrows<RuntimeException> {
        reconciliationService.generateReconciliationReport()
      }
    }
  }

  @DisplayName("GET /prison-balance/reconciliation/{prisonId}")
  @Nested
  inner class GenerateReconciliationReportForPrison {
    private val prisonId = "MDI"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prison-balance/reconciliation/$prisonId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prison-balance/reconciliation/$prisonId")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prison-balance/reconciliation/$prisonId")
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
      fun `will return no differences`() {
        webTestClient.get().uri("/prison-balance/reconciliation/$prisonId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS_UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return mismatch in counts with missing in DPS`() {
        nomisApi.stubGetPrisonBalance(
          prisonId = "MDI",
          prisonBalance = prisonBalanceDto().copy(
            accountBalances =
            listOf(prisonAccountBalanceDto(), prisonAccountBalanceDto().copy(accountCode = 2102)),
          ),
        )

        webTestClient.get().uri("/prison-balance/reconciliation/$prisonId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS_UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("prisonId").isEqualTo(prisonId)
          .jsonPath("nomisAccountCount").isEqualTo(2)
          .jsonPath("dpsAccountCount").isEqualTo(1)

        verify(telemetryClient).trackEvent(
          eq("prison-balance-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("prisonId", prisonId)
            assertThat(it).containsEntry("nomisAccountCount", "2")
            assertThat(it).containsEntry("dpsAccountCount", "1")
            assertThat(it).containsEntry("reason", "different-number-of-accounts")
          },
          isNull(),
        )
      }

      @Test
      fun `will return mismatch in counts with missing in Nomis`() {
        dpsFinanceServer.stubGetPrisonBalance(
          response = prisonAccounts().copy(
            items = listOf(
              prisonAccountDetails(),
              prisonAccountDetails().copy(accountCode = 2102),
            ),
          ),
        )

        webTestClient.get().uri("/prison-balance/reconciliation/$prisonId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS_UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("prisonId").isEqualTo(prisonId)
          .jsonPath("nomisAccountCount").isEqualTo(1)
          .jsonPath("dpsAccountCount").isEqualTo(2)

        verify(telemetryClient).trackEvent(
          eq("prison-balance-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("prisonId", prisonId)
            assertThat(it).containsEntry("nomisAccountCount", "1")
            assertThat(it).containsEntry("dpsAccountCount", "2")
            assertThat(it).containsEntry("reason", "different-number-of-accounts")
          },
          isNull(),
        )
      }

      @Test
      fun `will return balance mismatch`() {
        nomisApi.stubGetPrisonBalance(
          prisonBalance = prisonBalanceDto().copy(
            accountBalances = listOf(
              prisonAccountBalanceDto(),
              prisonAccountBalanceDto().copy(accountCode = 2102, balance = BigDecimal.valueOf(35.6)),
            ),
          ),
        )
        dpsFinanceServer.stubGetPrisonBalance(
          response = prisonAccounts().copy(
            items = listOf(
              prisonAccountDetails(),
              prisonAccountDetails().copy(accountCode = 2102),
            ),
          ),
        )

        webTestClient.get().uri("/prison-balance/reconciliation/$prisonId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS_UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("prisonId").isEqualTo(prisonId)
          .jsonPath("nomisAccountCount").isEqualTo(2)
          .jsonPath("dpsAccountCount").isEqualTo(2)

        verify(telemetryClient).trackEvent(
          eq("prison-balance-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("prisonId", prisonId)
            assertThat(it).containsEntry("nomisAccountCount", "2")
            assertThat(it).containsEntry("dpsAccountCount", "2")
            assertThat(it).containsEntry("reason", "different-prison-account-balance")
          },
          isNull(),
        )
      }
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("prison-balance-reports-reconciliation-report"), any(), isNull()) }
  }
}

fun generatePrisonId(prefix: String = "MDI", sequence: Long = 1) = "$prefix$sequence"
