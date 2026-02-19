package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.prisonTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.TransactionNomisApiMockServer.Companion.nomisPrisonTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PrisonTransactionResourceIntTest(
  @Autowired private val reconciliationService: PrisonTransactionReconciliationService,
  @Autowired private val nomisApi: TransactionNomisApiMockServer,
  @Autowired private val mappingApi: TransactionMappingApiMockServer,

) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>
  private val nomisPrisonerApi = NomisApiExtension.nomisApi
  private val dpsFinanceServer = FinanceDpsApiExtension.dpsFinanceServer

  @DisplayName("GET /prison-transactions/reports/reconciliation")
  @Nested
  inner class GenerateReconciliationReport {
    @Nested
    inner class Match {
      val entryDate = LocalDate.parse("2026-01-25")

      @BeforeEach
      fun setUp() {
        val nomisTransactionId1 = 1111L
        val nomisTransactionId3 = 3333L
        val dpsId1 = UUID.randomUUID().toString()
        val dpsId2 = UUID.randomUUID().toString()
        val dpsId3 = UUID.randomUUID().toString()

        nomisPrisonerApi.stubGetActivePrisons()

        nomisApi.stubGetPrisonTransactionsOn(
          prisonId = "ASI",
          date = entryDate,
          response = listOf(nomisPrisonTransaction(transactionId = nomisTransactionId1).copy(caseloadId = "ASI")),
        )
        nomisApi.stubGetPrisonTransactionsOn(date = entryDate)
        nomisApi.stubGetPrisonTransactionsOn(
          prisonId = "LEI",
          date = entryDate,
          response = listOf(nomisPrisonTransaction(transactionId = nomisTransactionId3).copy(caseloadId = "LEI")),
        )

        mappingApi.stubGetByNomisTransactionIdOrNull(
          nomisTransactionId = nomisTransactionId1,
          dpsTransactionId = dpsId1,
        )
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId2)
        mappingApi.stubGetByNomisTransactionIdOrNull(
          nomisTransactionId = nomisTransactionId3,
          dpsTransactionId = dpsId3,
        )

        dpsFinanceServer.stubGetPrisonTransaction(
          dpsTransactionId = dpsId1,
          response = prisonTransaction().copy(caseloadId = "ASI"),
        )
        dpsFinanceServer.stubGetPrisonTransaction(dpsTransactionId = dpsId2)
        dpsFinanceServer.stubGetPrisonTransaction(
          dpsTransactionId = dpsId3,
          response = prisonTransaction().copy(caseloadId = "LEI"),
        )
      }

      @Test
      fun `will output report requested telemetry`() = runTest {
        reconciliationService.generateReconciliationReport(entryDate)

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-requested"),
          check { assertThat(it).containsEntry("prisons", "3") },
          isNull(),
        )
      }

      @Test
      fun `will not report any mismatches`() = runTest {
        reconciliationService.generateReconciliationReport(entryDate)

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-report"),

          check {
            assertThat(it).containsEntry("success", "true")
            assertThat(it).containsEntry("mismatch-count", "0")
          },
          isNull(),
        )
      }

      @Test
      fun `should call to nomis to get the active prisons`() = runTest {
        reconciliationService.generateReconciliationReport(entryDate)

        awaitReportFinished()

        nomisApi.verify(
          WireMock.getRequestedFor(urlPathEqualTo("/prisons")),
        )
      }
    }

    @Nested
    inner class Mismatch {
      val nomisTransactionId1 = 1111L
      val nomisTransactionId3 = 3333L
      val dpsId1 = UUID.randomUUID().toString()
      val dpsId2 = UUID.randomUUID().toString()
      val dpsId3 = UUID.randomUUID().toString()
      val entryDate: LocalDate = LocalDate.parse("2026-01-25")

      @BeforeEach
      fun setUp() {
        nomisPrisonerApi.stubGetActivePrisons()

        // mock non-matching for ASI and LEI
        nomisApi.stubGetPrisonTransactionsOn(
          prisonId = "ASI",
          date = entryDate,
          response = listOf(
            nomisPrisonTransaction(transactionId = nomisTransactionId1).copy(
              type = "CRED",
              caseloadId = "ASI",
            ),
          ),
        )
        nomisApi.stubGetPrisonTransactionsOn(prisonId = "MDI", date = entryDate)
        nomisApi.stubGetPrisonTransactionsOn(
          prisonId = "LEI",
          date = entryDate,
          response = listOf(
            nomisPrisonTransaction(transactionId = nomisTransactionId3).copy(
              amount = BigDecimal(3.30),
              caseloadId = "LEI",
            ),
          ),
        )

        mappingApi.stubGetByNomisTransactionIdOrNull(
          nomisTransactionId = nomisTransactionId1,
          dpsTransactionId = dpsId1,
        )
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId2)
        mappingApi.stubGetByNomisTransactionIdOrNull(
          nomisTransactionId = nomisTransactionId3,
          dpsTransactionId = dpsId3,
        )

        dpsFinanceServer.stubGetPrisonTransaction(
          dpsTransactionId = dpsId1,
          response = prisonTransaction().copy(caseloadId = "ASI"),
        )
        dpsFinanceServer.stubGetPrisonTransaction(dpsTransactionId = dpsId2)
        dpsFinanceServer.stubGetPrisonTransaction(
          dpsTransactionId = dpsId3,
          response = prisonTransaction().copy(caseloadId = "LEI"),
        )
      }

      @Test
      fun `will output report requested telemetry`() = runTest {
        reconciliationService.generateReconciliationReport(entryDate)

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-requested"),
          check { assertThat(it).containsEntry("prisons", "3") },
          isNull(),
        )
      }

      @Test
      fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
        reconciliationService.generateReconciliationReport(entryDate)

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
            assertThat(it).containsEntry("success", "true")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          telemetryCaptor.capture(),
          isNull(),
        )

        val mismatchedRecords = telemetryCaptor.allValues.map { it["prisonId"] }

        assertThat(mismatchedRecords).containsOnly("ASI", "LEI")
        with(telemetryCaptor.allValues.find { it["prisonId"] == "ASI" }) {
          assertThat(this).containsEntry("prisonId", "ASI")
          assertThat(this).containsEntry("nomisTransactionId", "1111")
          assertThat(this).containsKey("dpsTransactionId")
          assertThat(this).containsEntry("nomisTransactionEntryCount", "1")
          assertThat(this).containsEntry("dpsTransactionEntryCount", "1")
          assertThat(this).containsEntry("differences", "type")
        }
        with(telemetryCaptor.allValues.find { it["prisonId"] == "LEI" }) {
          assertThat(this).containsEntry("prisonId", "LEI")
          assertThat(this).containsEntry("nomisTransactionId", "3333")
          assertThat(this).containsKey("dpsTransactionId")
          assertThat(this).containsEntry("nomisTransactionEntryCount", "1")
          assertThat(this).containsEntry("dpsTransactionEntryCount", "1")
          assertThat(this).containsEntry("differences", "entries")
        }
      }

      @Test
      fun `when initial prison count call fails the whole report fails`() = runTest {
        nomisPrisonerApi.stubGetActivePrisons(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<RuntimeException> {
          reconciliationService.generateReconciliationReport(entryDate)
        }
      }

      @Test
      fun `will attempt to complete a report even if some of the checks fail`() = runTest {
        dpsFinanceServer.stubGetPrisonTransaction(dpsTransactionId = dpsId2, response = null)

        reconciliationService.generateReconciliationReport(entryDate)

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-requested"),
          check { assertThat(it).containsEntry("prisons", "3") },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient, times(1)).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch-error"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
          },
          isNull(),
        )
      }
    }
  }

  @DisplayName("GET /prison-transactions/reports/reconciliation/{prisonId}")
  @Nested
  inner class GenerateReconciliationReportForPrison {
    @Nested
    inner class Match {
      val entryDate = LocalDate.parse("2026-01-25")
      val prisonId = "MDI"
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonTransactionsOn(date = entryDate)
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsFinanceServer.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will not report any mismatches`() = runTest {
        webTestClient.get().uri("/prison-transactions/reconciliation/$prisonId?date=$entryDate")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty
      }

      @Test
      fun `should call to nomis to get the transactions on a date`() = runTest {
        webTestClient.get().uri("/prison-transactions/reconciliation/$prisonId?date=$entryDate")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk

        nomisApi.verify(
          WireMock.getRequestedFor(urlEqualTo("/transactions/prison/$prisonId?entryDate=$entryDate")),
        )
      }

      @Test
      fun `should call to the mapping service`() = runTest {
        webTestClient.get().uri("/prison-transactions/reconciliation/$prisonId?date=$entryDate")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk

        mappingApi.verify(
          WireMock.getRequestedFor(urlEqualTo("/mapping/transactions/nomis-transaction-id/1234")),
        )
      }

      @Test
      fun `should call to the dps finance service`() = runTest {
        webTestClient.get().uri("/prison-transactions/reconciliation/$prisonId?date=$entryDate")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk

        dpsFinanceServer.verify(
          WireMock.getRequestedFor(urlEqualTo("/sync/general-ledger-transactions/$dpsId")),
        )
      }
    }

    @Nested
    inner class Mismatch {
      val entryDate: LocalDate = LocalDate.parse("2026-01-25")
      val prisonId = "MDI"
      val nomisId2 = 5678L
      val dpsId = UUID.randomUUID().toString()
      val dpsId2 = UUID.randomUUID().toString()

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonTransactionsOn(
          date = entryDate,
          response = listOf(
            nomisPrisonTransaction().copy(type = "CRED"),
            nomisPrisonTransaction().copy(generalLedgerEntrySequence = 2),
            nomisPrisonTransaction().copy(transactionId = nomisId2),
          ),
        )

        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        mappingApi.stubGetByNomisTransactionIdOrNull(nomisTransactionId = nomisId2, dpsTransactionId = dpsId2)
        val dpsEntry = prisonTransaction().generalLedgerEntries.first()

        dpsFinanceServer.stubGetPrisonTransaction(
          dpsTransactionId = dpsId,
          response =
          prisonTransaction().copy(generalLedgerEntries = listOf(dpsEntry, dpsEntry.copy(entrySequence = 2))),
        )
        dpsFinanceServer.stubGetPrisonTransaction(
          dpsTransactionId = dpsId2,
          response = prisonTransaction().copy(legacyTransactionId = nomisId2),
        )
      }

      @Test
      fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
        webTestClient.get().uri("/prison-transactions/reconciliation/$prisonId?date=$entryDate")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("[0].nomisTransactionId").isEqualTo("1234")
          .jsonPath("[0].dpsTransactionId").isEqualTo(dpsId)
          .jsonPath("[0].differences").isEqualTo(mapOf("type" to "nomis=CRED, dps=SPEN"))

        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsAllEntriesOf(
              mapOf(
                "prisonId" to "MDI",
                "nomisTransactionId" to "1234",
                "dpsTransactionId" to dpsId,
                "nomisTransactionEntryCount" to "2",
                "dpsTransactionEntryCount" to "2",
                "differences" to "type",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will attempt to complete a report even if some of the checks fail`() = runTest {
        dpsFinanceServer.stubGetPrisonTransaction(dpsTransactionId = dpsId, response = null)
        dpsFinanceServer.stubGetPrisonTransaction(
          dpsTransactionId = dpsId2,
          response = prisonTransaction().copy(legacyTransactionId = nomisId2, reference = "REF1"),
        )
        webTestClient.get().uri("/prison-transactions/reconciliation/$prisonId?date=$entryDate")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .consumeWith(System.out::println)
          .jsonPath("[0].nomisTransactionId").isEqualTo(nomisId2)
          .jsonPath("[0].dpsTransactionId").isEqualTo(dpsId2)
          .jsonPath("[0].differences").isEqualTo(mapOf("reference" to "nomis=ref 123, dps=REF1"))

        verify(telemetryClient, times(1)).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch-error"),
          check {
            assertThat(it).containsEntry("nomisTransactionId", "1234")
          },
          isNull(),
        )
        verify(telemetryClient, times(1)).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("nomisTransactionId", nomisId2.toString())
            assertThat(it).containsEntry("dpsTransactionId", dpsId2)
            assertThat(it).containsEntry("differences", "reference")
          },
          isNull(),
        )
      }
    }
  }

  @DisplayName("GET /prison-transactions/reconciliation/transaction/{transactionId}")
  @Nested
  inner class GenerateReconciliationReportForTransaction {
    private val nomisTransactionId = 1234L
    private val dpsTransactionId = UUID.randomUUID().toString()

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prison-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prison-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prison-transactions/reconciliation/transaction/$nomisTransactionId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setup() {
        nomisApi.stubGetPrisonTransaction(nomisTransactionId)
        mappingApi.stubGetByNomisTransactionIdOrNull(
          nomisTransactionId = nomisTransactionId,
          dpsTransactionId = dpsTransactionId,
        )
        dpsFinanceServer.stubGetPrisonTransaction(dpsTransactionId)
      }

      @Test
      fun `will return no differences`() {
        webTestClient.get().uri("/prison-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return mismatch data`() {
        dpsFinanceServer.stubGetPrisonTransaction(
          dpsTransactionId,
          response = prisonTransaction().copy(transactionType = "CR"),
        )

        webTestClient.get().uri("/prison-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("nomisTransactionId").isEqualTo(nomisTransactionId)
          .jsonPath("dpsTransactionId").isEqualTo(dpsTransactionId)
          .jsonPath("differences").isEqualTo(mapOf("type" to "nomis=SPEN, dps=CR"))

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("prison-transaction-reports-reconciliation-mismatch"),
            check {
              assertThat(it).containsAllEntriesOf(
                mapOf(
                  "prisonId" to "MDI",
                  "nomisTransactionId" to nomisTransactionId.toString(),
                  "dpsTransactionId" to dpsTransactionId,
                  "nomisTransactionEntryCount" to "1",
                  "dpsTransactionEntryCount" to "1",
                  "differences" to "type",
                ),
              )
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class UnhappyPath {
      @Test
      fun `when no transaction exists`() {
        nomisApi.stubGetPrisonTransaction(response = null)

        webTestClient.get().uri("/prison-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: Transaction not found 1234")

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `when no mapping exists`() {
        nomisApi.stubGetPrisonTransaction()
        mappingApi.stubGetByNomisTransactionIdOrNull(mapping = null)

        webTestClient.get().uri("/prison-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("prison-transaction-reports-reconciliation-mismatch-missing-mapping"),
            check {
              assertThat(it).containsAllEntriesOf(
                mapOf(
                  "nomisTransactionId" to nomisTransactionId.toString(),
                ),
              )
            },
            isNull(),
          )
        }
      }
    }
  }
  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("prison-transaction-reports-reconciliation-report"), any(), isNull()) }
  }
}
