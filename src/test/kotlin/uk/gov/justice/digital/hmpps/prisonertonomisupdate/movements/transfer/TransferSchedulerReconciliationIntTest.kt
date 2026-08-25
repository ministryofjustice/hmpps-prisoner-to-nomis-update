package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

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
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiExtension.Companion.transferSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

class TransferSchedulerReconciliationIntTest(
  @Autowired private val reconciliationService: TransferScheduleReconciliationService,
  @Autowired private val nomisApi: TransferSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: TransferSchedulerMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = transferSchedulerDpsApiServer
  private val nomisPrisonerApi = NomisApiExtension.nomisApi

  @DisplayName("Generate reconciliation report")
  @Nested
  inner class GenerateReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisPrisonerApi.stubGetAllPrisoners(
        offenderId = 0,
        pageSize = 100,
        prisoners = listOf(generateOffenderNo(sequence = 1)),
      )

      nomisApi.stubGetOffenderTransferMovements(offenderNo = "A0001TZ")
      dpsApi.stubGetTransferSchedulerReconciliation(personIdentifier = "A0001TZ")
      mappingApi.stubGetTransferSchedulerPrisonerMappingIds(prisonerNumber = "A0001TZ")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateTransferSchedulerReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateTransferSchedulerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "1")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("pages-count", "1")
        },
        isNull(),
      )
    }

    @Test
    fun `will report failure to reconcile prisoner`() = runTest {
      nomisApi.stubGetOffenderTransferMovements(status = INTERNAL_SERVER_ERROR)

      reconciliationService.generateTransferSchedulerReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-reconciliation-mismatch-error"),
        check {
          assertThat(it).containsEntry("offenderNo", "A0001TZ")
          assertThat(it).containsEntry("reason", "500 Internal Server Error from GET http://localhost:8082/movements/A0001TZ/transfer")
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("transfer-scheduler-reconciliation-report"), any(), isNull()) }
  }
}
