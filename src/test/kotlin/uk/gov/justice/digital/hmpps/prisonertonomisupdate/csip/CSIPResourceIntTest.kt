package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.WireMock
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
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.ReferenceData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Review
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCSIPsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CSIPResourceIntTest(
  @Autowired private val csipReconciliationService: CSIPReconciliationService,
  @Autowired private val csipNomisApi: CSIPNomisApiMockServer,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @DisplayName("CSIP reconciliation report")
  @Nested
  inner class GenerateCSIPReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      val numberOfActivePrisoners = 34L
      nomisApi.stubGetActivePrisonersInitialCount(numberOfActivePrisoners)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 0)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 1)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 2)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 3, 4)

      // mock non-matching for first, second and last prisoners
      csipDpsApi.stubGetCSIPsForPrisoner(
        "A0001TZ",
        dpsCsipRecord().copy(referral = dpsCsipRecord().referral.copy(incidentType = ReferenceData(code = "VIP"))),
        dpsCsipRecord(reviewOutcome = setOf(Review.Actions.REMAIN_ON_CSIP)),
      )

      csipNomisApi.stubGetCSIPsForReconciliation(
        "A0001TZ",
        response = PrisonerCSIPsResponse(
          offenderCSIPs = listOf(
            nomisCSIPResponse(closeCSIP = false),
          ),
        ),
      )

      csipDpsApi.stubGetCSIPsForPrisoner(
        "A0002TZ",
        dpsCsipRecord().copy(referral = dpsCsipRecord().referral.copy(incidentType = ReferenceData(code = "VIP"))),
        dpsCsipRecord().copy(),
      )
      csipNomisApi.stubGetCSIPsForReconciliation(
        "A0002TZ",
        response = PrisonerCSIPsResponse(
          offenderCSIPs = listOf(
            nomisCSIPResponse()
              .copy(type = CodeDescription(code = "VIP", description = "Viper Score")),
          ),
        ),
      )

      csipDpsApi.stubGetCSIPsForPrisoner(
        "A0034TZ",
        dpsCsipRecord().copy(referral = dpsCsipRecord().referral.copy(incidentType = ReferenceData(code = "VIP"))),
        dpsCsipRecord().copy(plan = dpsCsipRecord().plan!!.copy(identifiedNeeds = listOf())),

      )
      csipNomisApi.stubGetCSIPsForReconciliation(
        "A0034TZ",
        response = PrisonerCSIPsResponse(
          offenderCSIPs = listOf(
            nomisCSIPResponse().copy(type = CodeDescription(code = "VIP", description = "Viper Score")),
            nomisCSIPResponse().copy(type = CodeDescription(code = "INT", description = "Intimidation")),
            nomisCSIPResponse().copy(type = CodeDescription(code = "ISO", description = "Isolation")),
          ),
        ),
      )

      // all others have matching csips
      (3..<numberOfActivePrisoners).forEach {
        val offenderNo = generateOffenderNo(sequence = it)
        csipDpsApi.stubGetCSIPsForPrisoner(
          offenderNo,
          dpsCsipRecord(),
        )
        csipNomisApi.stubGetCSIPsForReconciliation(
          offenderNo,
          response = PrisonerCSIPsResponse(
            offenderCSIPs = listOf(nomisCSIPResponse()),
          ),
        )
      }
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      csipReconciliationService.generateCSIPReconciliationReport()

      verify(telemetryClient).trackEvent(
        eq("csip-reports-reconciliation-requested"),
        check { assertThat(it).containsEntry("active-prisoners", "34") },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() = runTest {
      csipReconciliationService.generateCSIPReconciliationReport()

      awaitReportFinished()
      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/prisoners/ids/active"))
          .withQueryParam("size", WireMock.equalTo("1")),
      )
      nomisApi.verify(
        // 34 prisoners will be spread over 4 pages of 10 prisoners each
        4,
        WireMock.getRequestedFor(urlPathEqualTo("/prisoners/ids/active"))
          .withQueryParam("size", WireMock.equalTo("10")),
      )
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
      csipReconciliationService.generateCSIPReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("csip-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
          assertThat(it).containsEntry("A0001TZ", "total-dps=2:total-nomis=1; missing-dps=0:missing-nomis=1")
          assertThat(it).containsEntry("A0002TZ", "total-dps=2:total-nomis=1; missing-dps=0:missing-nomis=1")
          assertThat(it).containsEntry("A0034TZ", "total-dps=2:total-nomis=3; missing-dps=2:missing-nomis=1")
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("csip-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      val mismatchedRecords = telemetryCaptor.allValues.map { it["offenderNo"] }

      assertThat(mismatchedRecords).containsOnly("A0001TZ", "A0002TZ", "A0034TZ")
      with(telemetryCaptor.allValues.find { it["offenderNo"] == "A0001TZ" }) {
        assertThat(this).containsEntry("bookingId", "1")
        assertThat(this).containsEntry("missingFromDps", "")
        assertThat(this).containsEntry(
          "missingFromNomis",
          "CSIPReportSummary(incidentTypeCode=VIP, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)",
        )
      }
      with(telemetryCaptor.allValues.find { it["offenderNo"] == "A0002TZ" }) {
        assertThat(this).containsEntry("bookingId", "2")
        assertThat(this).containsEntry("missingFromDps", "")
        assertThat(this).containsEntry(
          "missingFromNomis",
          "CSIPReportSummary(incidentTypeCode=INT, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)",
        )
      }
      with(telemetryCaptor.allValues.find { it["offenderNo"] == "A0034TZ" }) {
        assertThat(this).containsEntry("bookingId", "34")
        assertThat(this).containsEntry(
          "missingFromDps",
          "CSIPReportSummary(incidentTypeCode=INT, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true), " +
            "CSIPReportSummary(incidentTypeCode=ISO, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)",
        )
        assertThat(this).containsEntry(
          "missingFromNomis",
          "CSIPReportSummary(incidentTypeCode=INT, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=0, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)",
        )
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      csipDpsApi.stubGetCSIPsForPrisoner("A0002TZ", HttpStatus.INTERNAL_SERVER_ERROR)

      csipReconciliationService.generateCSIPReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(1)).trackEvent(
        eq("csip-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("csip-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsKeys("A0001TZ", "A0034TZ")
          assertThat(it).doesNotContainKey("A0002TZ")
        },
        isNull(),
      )
    }

    @Test
    fun `when initial prison count fails the whole report fails`() = runTest {
      nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 0, responseCode = 500)

      assertThrows<RuntimeException> {
        csipReconciliationService.generateCSIPReconciliationReport()
      }
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() = runTest {
      nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 2, responseCode = 500)

      csipReconciliationService.generateCSIPReconciliationReport()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("csip-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("csip-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
          assertThat(it).containsKeys("A0001TZ", "A0002TZ", "A0034TZ")
          assertThat(it).doesNotContainKey("A0033TZ")
        },
        isNull(),
      )
    }
  }

  @DisplayName("GET /csip/reconciliation/{prisonNumber}")
  @Nested
  inner class CSIPReconciliationForOffender {
    private val prisonNumber = "A1234BC"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/csip/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/csip/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/csip/reconciliation/$prisonNumber")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `return 404 when offender not found`() {
        csipNomisApi.stubGetCSIPsForReconciliation(
          offenderNo = "AB9999C",
          status = HttpStatus.NOT_FOUND,
        )
        webTestClient.get().uri("/csip/reconciliation/AB9999C")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
      }
    }

    @Nested
    inner class HappyPath {
      @Nested
      inner class OffenderWithNoCSIPEntries {
        @BeforeEach
        fun setup() {
          csipNomisApi.stubGetCSIPsForReconciliation(prisonNumber)
          csipDpsApi.stubGetCSIPsForPrisoner(prisonNumber)
        }

        @Test
        fun `will return no differences`() {
          webTestClient.get().uri("/csip/reconciliation/$prisonNumber")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody().isEmpty

          verifyNoInteractions(telemetryClient)
        }
      }

      @Nested
      inner class OffenderWithCSIPEntries {
        @BeforeEach
        fun setup() {
          csipNomisApi.stubGetCSIPsForReconciliation(
            prisonNumber,
            response = PrisonerCSIPsResponse(
              offenderCSIPs = listOf(nomisCSIPResponse()),
            ),
          )
          csipDpsApi.stubGetCSIPsForPrisoner(prisonNumber, dpsCsipRecord())
        }

        @Test
        fun `will return no differences`() {
          webTestClient.get().uri("/csip/reconciliation/$prisonNumber")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody().isEmpty

          verifyNoInteractions(telemetryClient)
        }

        @Test
        fun `will return mismatch with nomis`() {
          csipDpsApi.stubGetCSIPsForPrisoner(
            prisonNumber,
            dpsCsipRecord().copy(referral = dpsCsipRecord().referral.copy(incidentType = ReferenceData(code = "VIP"))),
            dpsCsipRecord(reviewOutcome = setOf(Review.Actions.REMAIN_ON_CSIP)),
          )

          webTestClient.get().uri("/csip/reconciliation/$prisonNumber")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("offenderNo").isEqualTo(prisonNumber)
            .jsonPath("nomisCSIPCount").isEqualTo(1)
            .jsonPath("dpsCSIPCount").isEqualTo(2)
            .jsonPath("missingFromNomis[0].incidentTypeCode").isEqualTo("VIP")
            .jsonPath("missingFromNomis[1].incidentTypeCode").isEqualTo("INT")
            .jsonPath("missingFromDps.size()").isEqualTo(1)

          verify(telemetryClient).trackEvent(
            eq("csip-reports-reconciliation-mismatch"),
            telemetryCaptor.capture(),
            isNull(),
          )

          with(telemetryCaptor.allValues[0]) {
            assertThat(this).containsEntry(
              "missingFromNomis",
              "CSIPReportSummary(incidentTypeCode=VIP, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true), " +
                "CSIPReportSummary(incidentTypeCode=INT, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=false)",
            )
            assertThat(this).containsEntry(
              "missingFromDps",
              "CSIPReportSummary(incidentTypeCode=INT, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)",
            )
          }
        }

        @Test
        fun `will return mismatch if one invalid date`() {
          csipNomisApi.stubGetCSIPsForReconciliation(
            prisonNumber,
            response = PrisonerCSIPsResponse(
              offenderCSIPs = listOf(nomisCSIPResponse()),
            ),
          )
          csipDpsApi.stubGetCSIPsForPrisoner(
            prisonNumber,
            dpsCsipRecord().copy(referral = dpsCsipRecord().referral.copy(incidentDate = LocalDate.parse("0919-09-14"))),
          )

          webTestClient.get().uri("/csip/reconciliation/$prisonNumber")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("offenderNo").isEqualTo(prisonNumber)
            .jsonPath("nomisCSIPCount").isEqualTo(1)
            .jsonPath("dpsCSIPCount").isEqualTo(1)
            .jsonPath("missingFromNomis.size()").isEqualTo(1)
            .jsonPath("missingFromDps.size()").isEqualTo(1)

          verify(telemetryClient).trackEvent(
            eq("csip-reports-reconciliation-mismatch"),
            telemetryCaptor.capture(),
            isNull(),
          )

          with(telemetryCaptor.allValues[0]) {
            assertThat(this).containsEntry(
              "missingFromNomis",
              "CSIPReportSummary(incidentTypeCode=INT, incidentDate=null, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)",
            )
            assertThat(this).containsEntry(
              "missingFromDps",
              "CSIPReportSummary(incidentTypeCode=INT, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)",
            )
          }
        }

        @Test
        fun `will not return mismatch if Nomis and Dps invalid incident dates`() {
          csipNomisApi.stubGetCSIPsForReconciliation(
            prisonNumber,
            response = PrisonerCSIPsResponse(
              offenderCSIPs = listOf(nomisCSIPResponse().copy(incidentDate = LocalDate.parse("0919-09-19"))),
            ),
          )
          csipDpsApi.stubGetCSIPsForPrisoner(
            prisonNumber,
            dpsCsipRecord().copy(referral = dpsCsipRecord().referral.copy(incidentDate = LocalDate.parse("0919-09-14"))),
          )

          webTestClient.get().uri("/csip/reconciliation/$prisonNumber")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody().isEmpty

          verifyNoInteractions(telemetryClient)
        }
      }
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("csip-reports-reconciliation-report"), any(), isNull()) }
  }
}
