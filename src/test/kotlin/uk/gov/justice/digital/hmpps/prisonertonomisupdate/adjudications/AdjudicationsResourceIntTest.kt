package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

class AdjudicationsResourceIntTest : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @DisplayName("PUT /adjudications/reports/reconciliation")
  @Nested
  inner class GenerateAdjudicationsReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      val numberOfActivePrisoners = 34L
      nomisApi.stubGetActivePrisonersInitialCount(numberOfActivePrisoners)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 0)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 1)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 2)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 3, 4)

      // mock non-matching for first and last prisoners
      adjudicationsApiServer.stubGetAdjudicationsByBookingId(1, listOf(aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10), adaPunishment(days = 15)))))
      nomisApi.stubGetAdaAwardSummary(
        bookingId = 1,
        adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
          bookingId = 1,
          offenderNo = "A0001TZ",
          adaSummaries = listOf(nomisSummary(days = 12)),
        ),
      )
      adjudicationsApiServer.stubGetAdjudicationsByBookingId(34, listOf(aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10)))))
      nomisApi.stubGetAdaAwardSummary(
        bookingId = 34,
        adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
          bookingId = 34,
          offenderNo = "A0034TZ",
          adaSummaries = listOf(nomisSummary(days = 12)),
        ),
      )

      // all others have matching ADAs
      (2..<numberOfActivePrisoners).forEach {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(it, listOf(aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10)))))
        nomisApi.stubGetAdaAwardSummary(
          bookingId = it,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = it,
            offenderNo = "A${it.toString().padStart(4, '0')}TZ",
            adaSummaries = listOf(nomisSummary(days = 10)),
          ),
        )
      }

      nomisApi.stubGetMergesFromDate("A0001TZ")
      nomisApi.stubGetMergesFromDate("A0034TZ")
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/adjudications/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(eq("adjudication-reports-reconciliation-requested"), check { assertThat(it).containsEntry("active-prisoners", "34") }, isNull())

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() {
      webTestClient.put().uri("/adjudications/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()
      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/prisoners/ids"))
          .withQueryParam("size", WireMock.equalTo("1"))
          .withQueryParam("active", WireMock.equalTo("true")),
      )
      nomisApi.verify(
        // 34 prisoners will be spread over 4 pages of 10 prisoners each
        4,
        WireMock.getRequestedFor(urlPathEqualTo("/prisoners/ids"))
          .withQueryParam("size", WireMock.equalTo("10"))
          .withQueryParam("active", WireMock.equalTo("true")),
      )
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() {
      webTestClient.put().uri("/adjudications/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("adjudication-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("A0001TZ", "1:2,12:25")
          assertThat(it).containsEntry("A0034TZ", "1:1,12:10")
        },
        isNull(),
      )

      verify(telemetryClient, times(2)).trackEvent(
        eq("adjudication-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      with(telemetryCaptor.allValues[0]) {
        assertThat(this).containsEntry("offenderNo", "A0001TZ")
        assertThat(this).containsEntry("bookingId", "1")
        assertThat(this).containsEntry("nomisAdaCount", "1")
        assertThat(this).containsEntry("dpsAdaCount", "2")
        assertThat(this).containsEntry("nomisAdaDays", "12")
        assertThat(this).containsEntry("dpsAdaDays", "25")
      }
      with(telemetryCaptor.allValues[1]) {
        assertThat(this).containsEntry("offenderNo", "A0034TZ")
        assertThat(this).containsEntry("bookingId", "34")
        assertThat(this).containsEntry("nomisAdaCount", "1")
        assertThat(this).containsEntry("dpsAdaCount", "1")
        assertThat(this).containsEntry("nomisAdaDays", "12")
        assertThat(this).containsEntry("dpsAdaDays", "10")
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() {
      adjudicationsApiServer.stubGetAdjudicationsByBookingIdWithError(1, 500)

      webTestClient.put().uri("/adjudications/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient, times(1)).trackEvent(
        eq("adjudication-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("adjudication-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("A0034TZ", "1:1,12:10")
        },
        isNull(),
      )
    }

    @Test
    fun `when initial prison count fails the whole report fails`() {
      nomisApi.stubGetActivePrisonersPageWithError(0, 500)

      webTestClient.put().uri("/adjudications/reports/reconciliation")
        .exchange()
        .expectStatus().is5xxServerError
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() {
      nomisApi.stubGetActivePrisonersPageWithError(2, 500)

      webTestClient.put().uri("/adjudications/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("adjudication-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("adjudication-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("A0001TZ", "1:2,12:25")
          assertThat(it).containsEntry("A0034TZ", "1:1,12:10")
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("adjudication-reports-reconciliation-report"), any(), isNull()) }
  }
}
