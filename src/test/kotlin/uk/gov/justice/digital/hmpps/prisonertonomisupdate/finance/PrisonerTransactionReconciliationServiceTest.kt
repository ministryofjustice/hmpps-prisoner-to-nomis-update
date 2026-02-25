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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.UUID

@SpringAPIServiceTest
@Import(
  PrisonerTransactionReconciliationService::class,
  NomisApiService::class,
  TransactionNomisApiService::class,
  TransactionMappingApiService::class,
  FinanceDpsApiService::class,
  FinanceConfiguration::class,
  RetryApiService::class,
  TransactionMappingApiMockServer::class,
  TransactionNomisApiMockServer::class,
)
class PrisonerTransactionReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  private val dpsApi = FinanceDpsApiExtension.dpsFinanceServer

  @Autowired
  private lateinit var nomisTransactionsApi: TransactionNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: TransactionMappingApiMockServer

  @Autowired
  private lateinit var service: PrisonerTransactionReconciliationService

  @Nested
  inner class TransactionsReconciliationMatch {

    @Nested
    inner class TransactionsMatch {

      @BeforeEach
      fun beforeEach() {
        val dpsId = UUID.randomUUID().toString()
        nomisTransactionsApi.stubGetPrisonerTransaction()
        mappingApi.stubGetByNomisTransactionIdOrNull(nomisTransactionId = 2345, dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonerTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(service.checkTransactionMatch(2345)).isNull()
      }

      @Test
      fun `will produce no telemetry`() = runTest {
        service.checkTransactionMatch(2345)
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
        nomisTransactionsApi.stubGetPrisonerTransaction(response = emptyList())
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(service.checkTransactionMatch(2345)).isNull()
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch failure`() = runTest {
        service.checkTransactionMatch(2345)
        verify(telemetryClient).trackEvent(
          eq("prisoner-transactions-reconciliation-mismatch-error"),
          eq(
            mapOf(
              "nomisTransactionId" to "2345",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class MissingMapping {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonerTransaction()
        mappingApi.stubGetByNomisTransactionIdOrNull(nomisTransactionId = 2345, mapping = null)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkTransactionMatch(2345)).isEqualTo(MismatchPrisonerTransaction(nomisTransactionId = 2345))
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch`() = runTest {
        service.checkTransactionMatch(2345)
        verify(telemetryClient).trackEvent(
          eq("prisoner-transactions-reconciliation-mismatch"),
          eq(
            mapOf(
              "nomisTransactionId" to "2345",
              "offenderNo" to "A1234AA",
              "reason" to "transaction-mapping-missing",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class MissingFromDps {
      val dpsId = UUID.randomUUID().toString()

      @BeforeEach
      fun beforeEach() {
        nomisTransactionsApi.stubGetPrisonerTransaction()
        mappingApi.stubGetByNomisTransactionIdOrNull(nomisTransactionId = 2345, dpsTransactionId = dpsId)
        dpsApi.stubGetPrisonerTransaction(dpsTransactionId = dpsId, response = null)
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkTransactionMatch(2345)).isEqualTo(MismatchPrisonerTransaction(nomisTransactionId = 2345))
        waitForEventProcessingToBeComplete()
      }

      @Test
      fun `telemetry will show mismatch as error`() = runTest {
        service.checkTransactionMatch(2345)
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("prisoner-transactions-reconciliation-mismatch"),
            eq(
              mapOf(
                "nomisTransactionId" to "2345",
                "dpsTransactionId" to dpsId,
                "offenderNo" to "A1234AA",
                "reason" to "dps-transaction-missing",
              ),
            ),
            isNull(),
          )
        }
      }
    }
  }
  private fun waitForEventProcessingToBeComplete() {
    await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
  }
}
