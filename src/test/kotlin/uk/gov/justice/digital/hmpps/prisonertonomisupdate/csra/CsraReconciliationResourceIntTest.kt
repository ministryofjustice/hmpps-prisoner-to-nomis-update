package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import software.amazon.awssdk.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.CsraDpsApiExtension.Companion.csraApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraCurrentRating
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraGetDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.Duration

class CsraReconciliationResourceIntTest(
  @Autowired private val reconciliationService: CsraReconciliationService,
) : IntegrationTestBase() {

  @Autowired
  private lateinit var csraNomisApi: CsraNomisApiMockServer

  @DisplayName("CSRA all reconciliation report")
  @Nested
  inner class GenerateAllCsraReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetAllPrisonersIdRanges(
        pageSize = 10,
        totalElements = 20,
      )
      nomisApi.stubGetAllPrisonersInRange(
        fromRootOffenderId = 0,
        toRootOffenderId = 10,
        firstOffenderNo = "A0001NN",
      )
      nomisApi.stubGetAllPrisonersInRange(
        fromRootOffenderId = 10,
        toRootOffenderId = 20,
        firstOffenderNo = "A0001NN",
      )
      for (i in 1..20) {
        val prisonNumber = "A${"$i".padStart(4, '0')}NN"
        stubCsras(prisonNumber, nomisCsra(), dpsCsra(CSRA_DPS_ID))
      }
    }

    @Nested
    inner class AllPrisoners {

      @Test
      fun `should pass active flag to Nomis id ranges`() = runTest {
        reconciliationService.generateReconciliationReportBatch(false)

        nomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/id-ranges"))
            .withQueryParam("active", equalTo("false"))
            .withQueryParam("size", equalTo("1000")),
        )
      }

      @Test
      fun `should pass active flag to Nomis ids in range`() = runTest {
        reconciliationService.generateReconciliationReportBatch(false)

        nomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/ids-in-range"))
            .withQueryParam("active", equalTo("false")),
        )
      }

      @Test
      fun `will output basic telemetry`() = runTest {
        reconciliationService.generateReconciliationReportBatch(false)

        verify(telemetryClient).trackEvent(
          eq("csra-reports-reconciliation-requested"),
          check { assertThat(it).containsEntry("activeOnly", "false") },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("csra-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("activeOnly", "false")
            assertThat(it).containsEntry("csra-count", "20")
            assertThat(it).containsEntry("page-count", "2")
            assertThat(it).containsEntry("mismatch-count", "0")
            assertThat(it).containsEntry("success", "true")
          },
          isNull(),
        )

        awaitReportFinished()
      }

      @Test
      fun `will output a mismatch when there is a difference in the DPS record`() = runTest {
        stubCsras(
          "A0002NN",
          nomisCsra(),
          null,
        )
        reconciliationService.generateReconciliationReportBatch(false)

        verify(telemetryClient).trackEvent(
          eq("csra-reports-reconciliation-report"),
          check {
            assertThat(it)
              .containsEntry("activeOnly", "false")
              .containsEntry("csra-count", "20")
              .containsEntry("page-count", "2")
              .containsEntry("mismatch-count", "1")
              .containsEntry("success", "true")
          },
          isNull(),
        )

        awaitReportFinished()
      }
    }

    @Nested
    inner class ActivePrisoners {

      @Test
      fun `should pass active flag to Nomis id ranges`() = runTest {
        reconciliationService.generateReconciliationReportBatch(true)

        nomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/id-ranges"))
            .withQueryParam("active", equalTo("true"))
            .withQueryParam("size", equalTo("1000")),
        )
      }

      @Test
      fun `should pass active flag to Nomis ids in range`() = runTest {
        reconciliationService.generateReconciliationReportBatch(true)

        nomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/ids-in-range"))
            .withQueryParam("active", equalTo("true")),
        )
      }

      @Test
      fun `will output basic telemetry`() = runTest {
        reconciliationService.generateReconciliationReportBatch(true)

        verify(telemetryClient).trackEvent(
          eq("csra-reports-reconciliation-requested"),
          check { assertThat(it).containsEntry("activeOnly", "true") },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("csra-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("activeOnly", "true")
            assertThat(it).containsEntry("csra-count", "20")
            assertThat(it).containsEntry("page-count", "2")
            assertThat(it).containsEntry("mismatch-count", "0")
            assertThat(it).containsEntry("success", "true")
          },
          isNull(),
        )

        awaitReportFinished()
      }
    }

    private fun awaitReportFinished() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("csra-reports-reconciliation-report"),
          any(),
          isNull(),
        )
      }
    }
  }

  @DisplayName("Reconcile one prisoner by offenderNo")
  @Nested
  inner class ManualCsraReconciliationReportOffenderNo {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/csra/reconciliation/$CSRA_OFFENDER_NO")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/csra/reconciliation/$CSRA_OFFENDER_NO")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/csra/reconciliation/$CSRA_OFFENDER_NO")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `will output a mismatch when there is a difference in the DPS record`() = runTest {
        stubCsras(
          CSRA_OFFENDER_NO,
          nomisCsra(),
          null,
        )

        webTestClient.mutate()
          .responseTimeout(Duration.ofMillis(60000))
          .build()
          .get().uri("/csra/reconciliation/$CSRA_OFFENDER_NO")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("nomis.id").isEqualTo("1-1")
          .jsonPath("differences[0].property").isEqualTo("current-csra")
          .jsonPath("differences[0].nomis").isEqualTo(1)
          .jsonPath("differences[0].dps").isEqualTo(0)

        verify(telemetryClient).trackEvent(
          eq("csra-reports-reconciliation-mismatch"),
          check {
            assertThat(it["prisoner"]).isEqualTo(CSRA_OFFENDER_NO)
            assertThat(it)
              .containsEntry("prisoner", CSRA_OFFENDER_NO)
              .containsEntry("current-csra", "Difference(property=current-csra, dps=0, nomis=1, dpsId=null, nomisId=1-1)")
          },
          isNull(),
        )
      }

      @Test
      fun `will return no differences when there is a match`() = runTest {
        stubCsras(
          CSRA_OFFENDER_NO,
          nomisCsra(),
          dpsCsra(CSRA_DPS_ID),
        )

        webTestClient.get().uri("/csra/reconciliation/$CSRA_OFFENDER_NO")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }
    }
  }

  private fun stubCsras(offenderNo: String, nomisResponse: CsraGetDto?, dpsResponse: CsraCurrentRating?) {
    nomisResponse?.let { csraNomisApi.stubGetCurrentCsraForPrisoner(offenderNo, it) }
      ?: csraNomisApi.stubGetCurrentCsraForPrisonerError(offenderNo, HttpStatusCode.NOT_FOUND)
    dpsResponse?.let { csraApi.stubGetCurrentCsra(offenderNo, it) }
      ?: csraApi.stubGetCurrentCsraError(offenderNo, HttpStatusCode.NOT_FOUND)
  }
}
