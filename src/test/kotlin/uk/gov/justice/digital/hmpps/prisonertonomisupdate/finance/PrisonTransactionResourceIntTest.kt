package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PrisonTransactionResourceIntTest(
  @Autowired private val reconciliationService: PrisonTransactionReconciliationService,
  @Autowired private val nomisApi: TransactionNomisApiMockServer,
  @Autowired private val mappingApi: TransactionMappingApiMockServer,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>
  private val dpsFinanceServer = FinanceDpsApiExtension.dpsFinanceServer

  @DisplayName("GET /prison-transaction/reconciliation/transaction/{transactionId}")
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
        mappingApi.stubGetByNomisTransactionIdOrNull(nomisTransactionId = nomisTransactionId, dpsTransactionId = dpsTransactionId)
        dpsFinanceServer.stubGetGeneralLedgerTransaction(dpsTransactionId)
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
        dpsFinanceServer.stubGetGeneralLedgerTransaction(
          dpsTransactionId,
          response = generalLedgerTransaction().copy(transactionType = "CR"),
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
      // TODO test when no mapping
      // TODO test when no transaction
    }
  }
}
