package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.ReferenceData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Review
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerCSIPsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

class CSIPResourceIntTest : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @DisplayName("PUT /csip/reports/reconciliation")
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
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/csip/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(eq("csip-reports-reconciliation-requested"), check { assertThat(it).containsEntry("active-prisoners", "34") }, isNull())

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() {
      webTestClient.put().uri("/csip/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

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
    fun `should emit a mismatched custom event for each mismatch along with a summary`() {
      webTestClient.put().uri("/csip/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

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
        assertThat(this).containsEntry("missingFromNomis", "CSIPReportSummary(incidentTypeCode=VIP, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)")
      }
      with(telemetryCaptor.allValues.find { it["offenderNo"] == "A0002TZ" }) {
        assertThat(this).containsEntry("bookingId", "2")
        assertThat(this).containsEntry("missingFromDps", "")
        assertThat(this).containsEntry("missingFromNomis", "CSIPReportSummary(incidentTypeCode=INT, incidentDate=2024-06-12, incidentTime=10:32:12, attendeeCount=1, factorCount=1, interviewCount=1, planCount=1, reviewCount=1, scsOutcomeCode=CUR, decisionOutcomeCode=OPE, csipClosedFlag=true)")
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
    fun `will attempt to complete a report even if some of the checks fail`() {
      csipDpsApi.stubGetCSIPsForPrisoner("A0002TZ", HttpStatus.INTERNAL_SERVER_ERROR)

      webTestClient.put().uri("/csip/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

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
    fun `when initial prison count fails the whole report fails`() {
      nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 0, responseCode = 500)

      webTestClient.put().uri("/csip/reports/reconciliation")
        .exchange()
        .expectStatus().is5xxServerError
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() {
      nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 2, responseCode = 500)

      webTestClient.put().uri("/csip/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

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

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("csip-reports-reconciliation-report"), any(), isNull()) }
  }
}
