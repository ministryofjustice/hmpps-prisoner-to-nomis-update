@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.prisonTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.TransactionNomisApiMockServer.Companion.nomisPrisonTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@SpringAPIServiceTest
@Import(
  PrisonTransactionReconciliationService::class,
  NomisApiService::class,
  TransactionNomisApiService::class,
  TransactionMappingApiService::class,
  FinanceDpsApiService::class,
  FinanceConfiguration::class,
  RetryApiService::class,
  TransactionMappingApiMockServer::class,
  TransactionNomisApiMockServer::class,
)
class PrisonTransactionReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  private val dpsApi = FinanceDpsApiExtension.dpsFinanceServer

  @Autowired
  private lateinit var nomisTransactionsApi: TransactionNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: TransactionMappingApiMockServer

  @Autowired
  private lateinit var service: PrisonTransactionReconciliationService

  @Nested
  inner class TransactionsReconciliationMatch {

    @Nested
    inner class TransactionsMatch {

      @BeforeEach
      fun beforeEach() {
        val dpsId = UUID.randomUUID().toString()
        nomisTransactionsApi.stubGetPrisonTransaction()
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkTransactionMatch(1234),
        ).isNull()
      }

      @Test
      fun `will produce no telemetry`() = runTest {
        service.checkTransactionMatch(1234)
        verifyNoInteractions(telemetryClient)
      }
    }
  }

  @Nested
  inner class TransactionsReconciliationMismatch {

    @Nested
    inner class MissingFromNomis {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(transactionId = 4343, response = null)
      }

      @Test
      fun `will throw not found exception`() = runTest {
        assertThrows<NotFound> {
          service.checkTransactionMatch(4343)
        }
      }

      @Test
      fun `will produce no telemetry`() = runTest {
        assertThrows<NotFound> {
          service.checkTransactionMatch(4343)
        }
        verifyNoInteractions(telemetryClient)
      }
    }

    @Nested
    inner class MissingMapping {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction()
        mappingApi.stubGetByNomisTransactionIdOrNull(mapping = null)
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(service.checkTransactionMatch(1234)).isNull()
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch-missing-mapping"),
          eq(mapOf("nomisTransactionId" to "1234")),
          isNull(),
        )
      }
    }

    @Nested
    inner class MissingFromDps {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction()
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId, response = null)
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(service.checkTransactionMatch(1234)).isNull()
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch as error`() = runTest {
        service.checkTransactionMatch(1234)
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("prison-transaction-reports-reconciliation-mismatch-error"),
            eq(mapOf("nomisTransactionId" to "1234")),
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class PrisonIdMismatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(caseloadId = "ASI")))
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(mapOf("prisonId" to "nomis=ASI, dps=MDI"))
        }
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "ASI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "1",
              "dpsTransactionEntryCount" to "1",
              "differences" to "prisonId",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class DescriptionMismatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(description = "CREDIT")))
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(mapOf("description" to "nomis=CREDIT, dps=General Ledger Account Transfer"))
        }
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "MDI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "1",
              "dpsTransactionEntryCount" to "1",
              "differences" to "description",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class TransactionTypeMismatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(type = "CR")))
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(mapOf("type" to "nomis=CR, dps=SPEN"))
        }
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "MDI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "1",
              "dpsTransactionEntryCount" to "1",
              "differences" to "type",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class ReferenceMismatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(reference = "Changed")))
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(mapOf("reference" to "nomis=Changed, dps=ref 123"))
        }
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "MDI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "1",
              "dpsTransactionEntryCount" to "1",
              "differences" to "reference",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class EntryDateMismatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(transactionTimestamp = LocalDateTime.parse("2026-01-27T23:30:00"))))
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(mapOf("entryDate" to "nomis=2026-01-27T23:30, dps=2021-02-03T04:05:09"))
        }
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "MDI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "1",
              "dpsTransactionEntryCount" to "1",
              "differences" to "entryDate",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class EntryCountMismatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction(), nomisPrisonTransaction()))
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        waitForEventProcessingToBeComplete()
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(mapOf("transactionEntryCount" to "nomis=2, dps=1"))
        }
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "MDI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "2",
              "dpsTransactionEntryCount" to "1",
              "differences" to "transactionEntryCount",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class EntryMismatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(amount = BigDecimal(22.22))))
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        waitForEventProcessingToBeComplete()
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(mapOf("entries" to "nomisOnly=[TransactionEntry(accountCode=1501, postingType=CR, amount=22.22, entrySequence=1)], dpsOnly=[TransactionEntry(accountCode=1501, postingType=CR, amount=5.40, entrySequence=1)]"))
        }
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "MDI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "1",
              "dpsTransactionEntryCount" to "1",
              "differences" to "entries",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class EntryMismatchAndEntryMatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(
          response =
          listOf(
            nomisPrisonTransaction(),
            nomisPrisonTransaction().copy(generalLedgerEntrySequence = 2, postingType = GeneralLedgerTransactionDto.PostingType.DR),
          ),
        )
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(
          dpsTransactionId = dpsId,
          response =
          prisonTransaction().copy(
            generalLedgerEntries =
            listOf(
              prisonTransaction().generalLedgerEntries.first(),
              prisonTransaction().generalLedgerEntries.first().copy(entrySequence = 2),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        waitForEventProcessingToBeComplete()
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(
            mapOf(
              "entries" to
                "nomisOnly=[TransactionEntry(accountCode=1501, postingType=DR, amount=5.40, entrySequence=2)], " +
                "dpsOnly=[TransactionEntry(accountCode=1501, postingType=CR, amount=5.40, entrySequence=2)]",
            ),
          )
        }
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "MDI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "2",
              "dpsTransactionEntryCount" to "2",
              "differences" to "entries",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class MultipleFieldMismatch {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonTransaction(
          response = listOf(
            nomisPrisonTransaction().copy(
              type = "CR",
              description = "CREDIT",
            ),
          ),
        )
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val result = service.checkTransactionMatch(1234)
        with(result!!) {
          assertThat(nomisTransactionId).isEqualTo(1234L)
          assertThat(dpsTransactionId).isNotEmpty
          assertThat(differences).isEqualTo(
            mapOf(
              "description" to "nomis=CREDIT, dps=General Ledger Account Transfer",
              "type" to "nomis=CR, dps=SPEN",
            ),
          )
        }
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(1234)
        verify(telemetryClient).trackEvent(
          eq("prison-transaction-reports-reconciliation-mismatch"),
          eq(
            mapOf(
              "prisonId" to "MDI",
              "nomisTransactionId" to "1234",
              "dpsTransactionId" to dpsId,
              "nomisTransactionEntryCount" to "1",
              "dpsTransactionEntryCount" to "1",
              "differences" to "description, type",
            ),
          ),
          isNull(),
        )
      }
    }
  }
  private fun waitForEventProcessingToBeComplete() {
    await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
  }
}
