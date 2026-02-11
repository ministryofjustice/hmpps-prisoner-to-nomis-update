@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
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
  inner class CheckTransactionsMatch {

    @Nested
    inner class WhenTransactionsMatch {

      @BeforeEach
      fun beforeEach() {
        val dpsId = UUID.randomUUID().toString()
        nomisTransactionsApi.stubGetPrisonTransaction()
        mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
        dpsApi.stubGetGeneralLedgerTransaction(dpsTransactionId = dpsId)
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkTransactionMatch(1234),
        ).isNull()
      }
    }
  }

  @Nested
  inner class WhenPrisonIdMismatch {
    val dpsId = UUID.randomUUID().toString()

    @BeforeEach
    fun beforeEach() {
      nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(caseloadId = "ASI")))
      mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
      dpsApi.stubGetGeneralLedgerTransaction(dpsTransactionId = dpsId)
    }

    @Test
    fun `will report a mismatch`() = runTest {
      val result = service.checkTransactionMatch(1234)
      with(result!!) {
        assertThat(nomisTransactionId).isEqualTo(1234L)
        assertThat(dpsTransactionId).isNotEmpty
        assertThat(differences).isEqualTo(mapOf("prisonId" to "nomis=ASI, dps=MDI"))
      }
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
  inner class WhenDescriptionMismatch {
    val dpsId = UUID.randomUUID().toString()

    @BeforeEach
    fun beforeEach() {
      nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(description = "CREDIT")))
      mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
      dpsApi.stubGetGeneralLedgerTransaction(dpsTransactionId = dpsId)
    }

    @Test
    fun `will report a mismatch`() = runTest {
      val result = service.checkTransactionMatch(1234)
      with(result!!) {
        assertThat(nomisTransactionId).isEqualTo(1234L)
        assertThat(dpsTransactionId).isNotEmpty
        assertThat(differences).isEqualTo(mapOf("description" to "nomis=CREDIT, dps=General Ledger Account Transfer"))
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
            "differences" to "description",
          ),
        ),
        isNull(),
      )
    }
  }

  @Nested
  inner class WhenTransactionTypeMismatch {
    val dpsId = UUID.randomUUID().toString()

    @BeforeEach
    fun beforeEach() {
      nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(type = "CR")))
      mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
      dpsApi.stubGetGeneralLedgerTransaction(dpsTransactionId = dpsId)
    }

    @Test
    fun `will report a mismatch`() = runTest {
      val result = service.checkTransactionMatch(1234)
      with(result!!) {
        assertThat(nomisTransactionId).isEqualTo(1234L)
        assertThat(dpsTransactionId).isNotEmpty
        assertThat(differences).isEqualTo(mapOf("type" to "nomis=CR, dps=SPEN"))
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
            "differences" to "type",
          ),
        ),
        isNull(),
      )
    }
  }

  @Nested
  inner class WhenMultipleFieldMismatch {
    val dpsId = UUID.randomUUID().toString()

    @BeforeEach
    fun beforeEach() {
      nomisTransactionsApi.stubGetPrisonTransaction(response = listOf(nomisPrisonTransaction().copy(type = "CR", description = "CREDIT")))
      mappingApi.stubGetByNomisTransactionIdOrNull(dpsTransactionId = dpsId)
      dpsApi.stubGetGeneralLedgerTransaction(dpsTransactionId = dpsId)
    }

    @Test
    fun `will report a mismatch`() = runTest {
      val result = service.checkTransactionMatch(1234)
      with(result!!) {
        assertThat(nomisTransactionId).isEqualTo(1234L)
        assertThat(dpsTransactionId).isNotEmpty
        assertThat(differences).isEqualTo(mapOf("description" to "nomis=CREDIT, dps=General Ledger Account Transfer", "type" to "nomis=CR, dps=SPEN"))
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
            "differences" to "description, type",
          ),
        ),
        isNull(),
      )
    }
  }
}
