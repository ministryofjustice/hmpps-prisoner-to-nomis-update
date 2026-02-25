package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.offenderTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.prisonerTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerTransactionIdResponse
import java.time.LocalDate
import java.util.UUID

class PrisonerTransactionsReconciliationIntTest(
  @Autowired private val reconciliationService: PrisonerTransactionReconciliationService,
  @Autowired private val nomisApi: TransactionNomisApiMockServer,
  @Autowired private val mappingApi: TransactionMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = FinanceDpsApiExtension.dpsFinanceServer

  @DisplayName("Prisoner transactions reconciliation report")
  @Nested
  inner class GenerateAllPrisonerTransactionsReconciliationReportBatch {

    val date: LocalDate = LocalDate.parse("2021-02-03")
    val nomisId1234 = 1234L
    val nomisId2345 = 2345L
    val nomisId3456 = 3456L
    val dpsId1234 = UUID.randomUUID().toString()
    val dpsId2345 = UUID.randomUUID().toString()
    val dpsId3456 = UUID.randomUUID().toString()

    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetPrisonerTransactionIdsByLastId(
        lastTransactionId = 0,
        entryDate = date,
        size = 2,
        content = listOf(
          PrisonerTransactionIdResponse(nomisId1234),
          PrisonerTransactionIdResponse(nomisId2345),
        ),
      )
      nomisApi.stubGetPrisonerTransactionIdsByLastId(
        lastTransactionId = nomisId2345,
        entryDate = date,
        size = 2,
        content = listOf(
          PrisonerTransactionIdResponse(nomisId3456),
        ),
      )

      nomisApi.stubGetPrisonerTransaction(nomisId1234)
      nomisApi.stubGetPrisonerTransaction(nomisId2345)
      nomisApi.stubGetPrisonerTransaction(nomisId3456)
      mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId1234)
      mappingApi.stubGetByNomisTransactionIdOrNull(nomisTransactionId = nomisId2345, dpsTransactionId = dpsId2345)
      mappingApi.stubGetByNomisTransactionIdOrNull(nomisTransactionId = nomisId3456, dpsTransactionId = dpsId3456)
      dpsApi.stubGetPrisonerTransaction(
        dpsTransactionId = dpsId1234,
        response = prisonerTransaction(nomisTransactionId = nomisId1234, dpsId = UUID.fromString(dpsId1234)),
      )
      dpsApi.stubGetPrisonerTransaction(
        dpsTransactionId = dpsId2345,
        response = prisonerTransaction(nomisTransactionId = nomisId2345, dpsId = UUID.fromString(dpsId2345)),
      )
      dpsApi.stubGetPrisonerTransaction(
        dpsTransactionId = dpsId3456,
        response = prisonerTransaction(nomisTransactionId = nomisId3456, dpsId = UUID.fromString(dpsId3456)),
      )
    }

    @Nested
    inner class Match {
      @Test
      fun `will output report requested telemetry`() = runTest {
        reconciliationService.generateReconciliationReportBatch(LocalDate.parse("2021-02-03"))
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prisoner-transactions-reconciliation-requested"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will make 2 calls for each transaction id set`() = runTest {
        reconciliationService.generateReconciliationReportBatch(LocalDate.parse("2021-02-03"))
        awaitReportFinished()
        nomisApi.verify(
          2,
          WireMock.getRequestedFor(urlPathMatching("/transactions/from/\\d+")),
        )
      }

      @Test
      fun `will output report`() = runTest {
        reconciliationService.generateReconciliationReportBatch(LocalDate.parse("2021-02-03"))
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prisoner-transactions-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "0")
            assertThat(it).containsEntry("pages-count", "2")
            assertThat(it).containsEntry("transaction-count", "3")
            assertThat(it).containsEntry("success", "true")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class Mismatch {
      @BeforeEach
      fun setUp() {
        dpsApi.stubGetPrisonerTransaction(
          dpsTransactionId = dpsId2345,
          response = prisonerTransaction(nomisTransactionId = nomisId2345, dpsId = UUID.fromString(dpsId2345)).copy(
            transactions =
            listOf(offenderTransaction().copy(entrySequence = 1), offenderTransaction().copy(entrySequence = 2)),
          ),
        )
      }

      @Test
      fun `will output report requested telemetry`() = runTest {
        reconciliationService.generateReconciliationReportBatch(LocalDate.parse("2021-02-03"))

        verify(telemetryClient).trackEvent(
          eq("prisoner-transactions-reconciliation-requested"),
          any(),
          isNull(),
        )
        awaitReportFinished()
      }

      @Test
      fun `will output report`() = runTest {
        reconciliationService.generateReconciliationReportBatch(LocalDate.parse("2021-02-03"))
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prisoner-transactions-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "1")
            assertThat(it).containsEntry("pages-count", "2")
            assertThat(it).containsEntry("transaction-count", "3")
            assertThat(it).containsEntry("success", "true")
          },
          isNull(),
        )
      }

      @Test
      fun `will output a mismatch for mismatch entries`() = runTest {
        reconciliationService.generateReconciliationReportBatch(LocalDate.parse("2021-02-03"))
        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("prisoner-transactions-reconciliation-mismatch"),
          eq(
            mapOf(
              "nomisTransactionId" to "2345",
              "dpsTransactionId" to dpsId2345,
              "offenderNo" to "A1234AA",
              "reason" to "transaction-different-details",
            ),
          ),
          isNull(),
        )
      }
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("prisoner-transactions-reconciliation-report"), any(), isNull()) }
    }
  }
}
