package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_GATEWAY
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.PrisonPersonDpsApiExtension.Companion.prisonPersonDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

@TestPropertySource(properties = ["reports.prisonperson.reconciliation.page-size=3"])
class PrisonPersonReconIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var prisonPersonNomisApi: PrisonPersonNomisApiMockServer

  @Autowired
  private lateinit var reconciliationService: PrisonPersonReconService

  @Nested
  inner class SinglePrisoner {
    @Test
    fun `should do nothing if no differences`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = 180, weight = 80)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = 180, weight = 80)

      reconciliationService.checkPrisoner("A1234AA")
        .also { assertThat(it).isNull() }

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should report differences to height and weight`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = 170, weight = 70)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = 180, weight = 80)

      reconciliationService.checkPrisoner("A1234AA")
        .also { assertThat(it).isEqualTo("A1234AA") }

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "differences" to "height, weight",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report differences if null in DPS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = null, weight = null)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = 180, weight = 80)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsAllEntriesOf(
            mapOf("differences" to "height, weight"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report differences if null in NOMIS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = 170, weight = 70)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = null, weight = null)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsAllEntriesOf(
            mapOf("differences" to "height, weight"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report differences if missing from DPS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(NOT_FOUND)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = 180, weight = 80)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "differences" to "height, weight",
              "dpsPrisoner" to "null",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report differences if missing from NOMIS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = 170, weight = 70)
      prisonPersonNomisApi.stubGetReconciliation(NOT_FOUND)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "differences" to "height, weight",
              "nomisPrisoner" to "null",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should not report differences if null in both`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA", height = null, weight = null)
      prisonPersonNomisApi.stubGetReconciliation(offenderNo = "A1234AA", height = null, weight = null)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should not report differences if missing from both`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(NOT_FOUND)
      prisonPersonNomisApi.stubGetReconciliation(NOT_FOUND)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient, never()).trackEvent(anyString(), anyMap(), isNull())
    }

    @Test
    fun `should report errors from DPS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(HttpStatus.INTERNAL_SERVER_ERROR)
      prisonPersonNomisApi.stubGetReconciliation("A1234AA")

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "error" to "500 Internal Server Error from GET http://localhost:8097/prisoners/A1234AA/physical-attributes",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should report errors from NOMIS`() = runTest {
      prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = "A1234AA")
      prisonPersonNomisApi.stubGetReconciliation(BAD_GATEWAY)

      reconciliationService.checkPrisoner("A1234AA")

      verify(telemetryClient).trackEvent(
        eq("prison-person-reconciliation-prisoner-error"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "offenderNo" to "A1234AA",
              "error" to "502 Bad Gateway from GET http://localhost:8082/prisoners/A1234AA/prison-person/reconciliation",
            ),
          )
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class FullReconciliation {
    private val noActivePrisoners = 7L
    private val pageSize = 3L

    private fun stubPages() {
      nomisApi.apply {
        stubGetActivePrisonersInitialCount(noActivePrisoners)
        stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 0, pageSize = pageSize, numberOfElements = pageSize)
        stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 1, pageSize = pageSize, numberOfElements = pageSize)
        stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 2, pageSize = pageSize, numberOfElements = 1)
      }
    }

    private fun forEachPrisoner(action: (offenderNo: String) -> Any) {
      (1..noActivePrisoners)
        .map { generateOffenderNo(sequence = it) }
        .forEach { offenderNo -> action(offenderNo) }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setup() {
        reset(telemetryClient)
        stubPages()
        forEachPrisoner { offenderNo ->
          prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = offenderNo, height = 180, weight = 80)
          prisonPersonNomisApi.stubGetReconciliation(offenderNo = offenderNo, height = 180, weight = 80)
        }

        runReconciliation()
      }

      @Test
      fun `should run a reconciliation with no problems`() {
        // should publish requested telemetry
        verify(telemetryClient).trackEvent(
          eq("prison-person-reconciliation-report-requested"),
          check {
            assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
          },
          isNull(),
        )

        // should request pages of prisoners
        nomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/ids/active"))
            .withQueryParam("size", equalTo("1")),
        )
        nomisApi.verify(
          3,
          getRequestedFor(urlPathEqualTo("/prisoners/ids/active"))
            .withQueryParam("size", equalTo("$pageSize")),
        )

        // should call DPS and NOMIS for each prisoner
        forEachPrisoner { offenderNo ->
          prisonPersonNomisApi.verify(
            getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/prison-person/reconciliation")),
          )
          prisonPersonDpsApi.verify(
            getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/physical-attributes")),
          )
        }

        // `should publish success telemetry
        verify(telemetryClient).trackEvent(
          eq("prison-person-reconciliation-report-success"),
          check {
            assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
            assertThat(it).containsEntry("mismatch-count", "0")
            assertThat(it).containsEntry("mismatch-prisoners", "[]")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class Failures {
      @Nested
      inner class PrisonerHeightMismatch {
        @BeforeEach
        fun `set up with mismatched prisoner height`() {
          reset(telemetryClient)
          stubPages()
          forEachPrisoner { offenderNo ->
            prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = offenderNo, height = 180, weight = 80)
            // the 4th prisoner's height is different in DPS and NOMIS
            if (offenderNo != "A0004TZ") {
              prisonPersonNomisApi.stubGetReconciliation(offenderNo = offenderNo, height = 180, weight = 80)
            } else {
              prisonPersonNomisApi.stubGetReconciliation(offenderNo = offenderNo, height = 170, weight = 80)
            }
          }

          runReconciliation(expectSuccess = false)
        }

        @Test
        fun `should handle a mismatched height`() {
          // should publish failure telemetry for prisoner
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-prisoner-failed"),
            check {
              assertThat(it).containsAllEntriesOf(
                mapOf(
                  "offenderNo" to "A0004TZ",
                  "differences" to "height",
                ),
              )
            },
            isNull(),
          )

          // should publish failure telemetry for report
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-report-failed"),
            check {
              assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
              assertThat(it).containsEntry("mismatch-count", "1")
              assertThat(it).containsEntry("mismatch-prisoners", "[A0004TZ]")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class PrisonerWeightMismatch {
        @BeforeEach
        fun `set up with mismatched prisoner weight`() {
          reset(telemetryClient)
          stubPages()
          forEachPrisoner { offenderNo ->
            prisonPersonNomisApi.stubGetReconciliation(offenderNo = offenderNo, height = 180, weight = 80)
            // the 1st prisoner's weight is different in DPS and NOMIS
            if (offenderNo != "A0001TZ") {
              prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = offenderNo, height = 180, weight = 80)
            } else {
              prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = offenderNo, height = 180, weight = 70)
            }
          }

          runReconciliation(expectSuccess = false)
        }

        @Test
        fun `should handle a mismatched weight`() {
          // should publish failure telemetry for prisoner
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-prisoner-failed"),
            check {
              assertThat(it).containsAllEntriesOf(
                mapOf(
                  "offenderNo" to "A0001TZ",
                  "differences" to "weight",
                ),
              )
            },
            isNull(),
          )

          // should publish report completed successfully telemetry
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-report-failed"),
            check {
              assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
              assertThat(it).containsEntry("mismatch-count", "1")
              assertThat(it).containsEntry("mismatch-prisoners", "[A0001TZ]")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class Errors {
      @Nested
      inner class StartReportErrors {
        @Test
        fun `should fail and publish error telemetry if error getting prisoner count`() {
          reset(telemetryClient)
          nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 0, pageSize = 1, responseCode = 502)

          webTestClient.put().uri("/prisonperson/reports/reconciliation")
            .exchange()
            .expectStatus().is5xxServerError
        }
      }

      @Nested
      inner class GetPageOfPrisonerErrors {
        @BeforeEach
        fun `set up with error on 2nd page`() {
          reset(telemetryClient)
          nomisApi.apply {
            stubGetActivePrisonersInitialCount(noActivePrisoners)
            stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 0, pageSize = pageSize, numberOfElements = pageSize)
            // fail to retrieve page 1
            stubGetActivePrisonersPageWithError(pageNumber = 1, pageSize = pageSize, responseCode = 502)
            stubGetActivePrisonersPage(noActivePrisoners, pageNumber = 2, pageSize = pageSize, numberOfElements = 1)
          }
          forEachPrisoner { offenderNo ->
            prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = offenderNo, height = 180, weight = 80)
            prisonPersonNomisApi.stubGetReconciliation(offenderNo = offenderNo, height = 180, weight = 80)
          }

          runReconciliation()
        }

        @Test
        fun `should handle an error getting a full page of prisoners`() {
          // should call DPS and NOMIS for each prisoner on successful pages
          forEachPrisoner { offenderNo ->
            // for prisoners from the 2nd page, we should call the APIs zero times
            val count = if (listOf("A0004TZ", "A0005TZ", "A0006TZ").contains(offenderNo)) 0 else 1
            prisonPersonNomisApi.verify(
              count,
              getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/prison-person/reconciliation")),
            )
            prisonPersonDpsApi.verify(
              count,
              getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/physical-attributes")),
            )
          }

          // should publish error telemetry for page
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-page-error"),
            check {
              assertThat(it).containsEntry("page", "1")
              assertThat(it).containsEntry(
                "error",
                "502 Bad Gateway from GET http://localhost:8082/prisoners/ids/active",
              )
            },
            isNull(),
          )

          // should publish success telemetry for report
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-report-success"),
            check {
              assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
              assertThat(it).containsEntry("mismatch-count", "0")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class GetSinglePrisonerErrors {
        @BeforeEach
        fun `set up with error for single prisoner`() {
          reset(telemetryClient)
          stubPages()
          forEachPrisoner { offenderNo ->
            // the 4th prisoner's NOMIS call returns an error
            if (offenderNo != "A0004TZ") {
              prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = offenderNo, height = 180, weight = 80)
              prisonPersonNomisApi.stubGetReconciliation(offenderNo = offenderNo, height = 180, weight = 80)
            } else {
              prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = offenderNo, height = 180, weight = 80)
              prisonPersonNomisApi.stubGetReconciliation(BAD_GATEWAY)
            }
          }

          runReconciliation()
        }

        @Test
        fun `should handle an error getting a single prisoner`() {
          // should publish error telemetry for prisoner
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-prisoner-error"),
            check {
              assertThat(it).containsEntry("offenderNo", "A0004TZ")
              assertThat(it).containsEntry(
                "error",
                "502 Bad Gateway from GET http://localhost:8082/prisoners/A0004TZ/prison-person/reconciliation",
              )
            },
            isNull(),
          )

          // should publish success telemetry for report despite error
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-report-success"),
            check {
              assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
              assertThat(it).containsEntry("mismatch-count", "0")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class GetSinglePrisonerSucceedsWithRetries {
        @BeforeEach
        fun `set up with retryable errors for one prisoner`() {
          reset(telemetryClient)
          stubPages()
          forEachPrisoner { offenderNo ->
            // the last prisoner's NOMIS and DPS calls fail then work on a retry
            if (offenderNo != "A0007TZ") {
              prisonPersonDpsApi.stubGetPhysicalAttributes(offenderNo = offenderNo, height = 180, weight = 80)
              prisonPersonNomisApi.stubGetReconciliation(offenderNo = offenderNo, height = 180, weight = 80)
            } else {
              prisonPersonDpsApi.stubGetPhysicalAttributesWithRetry(offenderNo = offenderNo, height = 180, weight = 80)
              prisonPersonNomisApi.stubGetReconciliationWithRetry(offenderNo = offenderNo, height = 180, weight = 80)
            }
          }

          runReconciliation()
        }

        @Test
        fun `should handle handle connection failures and retry both DPS and NOMIS`() {
          // should retry the NOMIS and DPS calls for the failed prisoner
          prisonPersonNomisApi.verify(
            2,
            getRequestedFor(urlPathEqualTo("/prisoners/A0007TZ/prison-person/reconciliation")),
          )
          prisonPersonDpsApi.verify(
            2,
            getRequestedFor(urlPathEqualTo("/prisoners/A0007TZ/physical-attributes")),
          )

          // should NOT publish error telemetry for prisoner
          verify(telemetryClient, times(0)).trackEvent(
            eq("prison-person-reconciliation-prisoner-error"),
            anyMap(),
            isNull(),
          )

          // should publish success telemetry
          verify(telemetryClient).trackEvent(
            eq("prison-person-reconciliation-report-success"),
            check {
              assertThat(it).containsEntry("active-prisoners", noActivePrisoners.toString())
              assertThat(it).containsEntry("mismatch-count", "0")
            },
            isNull(),
          )
        }
      }
    }

    private fun runReconciliation(expectSuccess: Boolean = true) {
      webTestClient.put().uri("/prisonperson/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
        .also {
          awaitReportFinished(expectSuccess)
        }
    }

    private fun awaitReportFinished(expectSuccess: Boolean = true) {
      expectSuccess
        .let { if (it) "success" else "failed" }
        .also {
          await untilAsserted { verify(telemetryClient).trackEvent(eq("prison-person-reconciliation-report-$it"), any(), isNull()) }
        }
    }
  }
}
