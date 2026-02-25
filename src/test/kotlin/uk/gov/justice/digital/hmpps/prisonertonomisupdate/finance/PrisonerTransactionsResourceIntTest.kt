@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.prisonerTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import java.util.UUID
@ExtendWith(MockitoExtension::class)
class PrisonerTransactionsResourceIntTest(
  @Autowired private val nomisApi: TransactionNomisApiMockServer,
  @Autowired private val mappingApi: TransactionMappingApiMockServer,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  private val dpsApi = FinanceDpsApiExtension.dpsFinanceServer

  @DisplayName("GET /prisoner-transactions/reconciliation/transaction/{transactionId}")
  @Nested
  inner class GenerateReconciliationReportForTransaction {
    private val nomisTransactionId = 1234L
    private val dpsTransactionId = UUID.randomUUID().toString()

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prisoner-transactions/reconciliation/transaction/$nomisTransactionId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class MatchAndMismatch {

      @BeforeEach
      fun setup() {
        nomisApi.stubGetPrisonerTransaction(nomisTransactionId)
        mappingApi.stubGetByNomisTransactionIdOrNull(
          nomisTransactionId = nomisTransactionId,
          dpsTransactionId = dpsTransactionId,
        )
        dpsApi.stubGetPrisonerTransaction(
          dpsTransactionId = dpsTransactionId,
          response = prisonerTransaction(nomisTransactionId = nomisTransactionId, dpsId = UUID.fromString(dpsTransactionId)),
        )
      }

      @Test
      fun `will return no differences`() {
        webTestClient.get().uri("/prisoner-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return mismatch data`() {
        dpsApi.stubGetPrisonerTransaction(
          dpsTransactionId,
          response = prisonerTransaction(nomisTransactionId = nomisTransactionId, dpsId = UUID.fromString(dpsTransactionId)).copy(caseloadId = "SWI"),
        )

        webTestClient.get().uri("/prisoner-transactions/reconciliation/transaction/$nomisTransactionId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("nomisTransactionId").isEqualTo(nomisTransactionId)
          .jsonPath("dpsTransactionId").isEqualTo(dpsTransactionId)
          .jsonPath("prisonNumber").isEqualTo("A1234AA")
          .jsonPath("reason").isEqualTo("transaction-different-details")
          .jsonPath("nomisTransaction.prisonId").isEqualTo("MDI")
          .jsonPath("dpsTransaction.prisonId").isEqualTo("SWI")

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("prisoner-transactions-reconciliation-mismatch"),
            check {
              assertThat(it).containsAllEntriesOf(
                mapOf(
                  "nomisTransactionId" to nomisTransactionId.toString(),
                  "dpsTransactionId" to dpsTransactionId,
                  "prisonNumber" to "A1234AA",
                  "reason" to "transaction-different-details",
                ),
              )
            },
            isNull(),
          )
        }
      }
    }
  }
}
