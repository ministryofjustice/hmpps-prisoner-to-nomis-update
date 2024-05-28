package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

class IncentivesResourceIntTest : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @DisplayName("PUT /incentives/reports/reconciliation")
  @Nested
  inner class GenerateIncentiveReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)

      val numberOfActivePrisoners = 34L
      nomisApi.stubGetActivePrisonersInitialCount(numberOfActivePrisoners)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 0)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 1)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 2)
      nomisApi.stubGetActivePrisonersPage(numberOfActivePrisoners, 3, 4)
      (1..numberOfActivePrisoners).forEach {
        nomisApi.stubCurrentIncentiveGet(it, "STD")
        // every 10th prisoner has an ENH incentive
        incentivesApi.stubCurrentIncentiveGet(it, if (it.toInt() % 10 == 0) "ENH" else "STD")
      }
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/incentives/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(eq("incentives-reports-reconciliation-requested"), check { assertThat(it).containsEntry("active-prisoners", "34") }, isNull())

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() {
      // given "reports.incentives.reconciliation.page-size=10"

      webTestClient.put().uri("/incentives/reports/reconciliation")
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
      webTestClient.put().uri("/incentives/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("incentives-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
          assertThat(it).containsEntry("A0010TZ", "STD:ENH")
          assertThat(it).containsEntry("A0020TZ", "STD:ENH")
          assertThat(it).containsEntry("A0030TZ", "STD:ENH")
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("incentives-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      with(telemetryCaptor.allValues[0]) {
        assertThat(this).containsEntry("offenderNo", "A0010TZ")
        assertThat(this).containsEntry("bookingId", "10")
        assertThat(this).containsEntry("nomisIncentiveLevel", "STD")
        assertThat(this).containsEntry("dpsIncentiveLevel", "ENH")
      }
      with(telemetryCaptor.allValues[1]) {
        assertThat(this).containsEntry("offenderNo", "A0020TZ")
        assertThat(this).containsEntry("bookingId", "20")
        assertThat(this).containsEntry("nomisIncentiveLevel", "STD")
        assertThat(this).containsEntry("dpsIncentiveLevel", "ENH")
      }
      with(telemetryCaptor.allValues[2]) {
        assertThat(this).containsEntry("offenderNo", "A0030TZ")
        assertThat(this).containsEntry("bookingId", "30")
        assertThat(this).containsEntry("nomisIncentiveLevel", "STD")
        assertThat(this).containsEntry("dpsIncentiveLevel", "ENH")
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() {
      nomisApi.stubCurrentIncentiveGetWithError(2, 500)
      incentivesApi.stubCurrentIncentiveGetWithError(20, 500)

      webTestClient.put().uri("/incentives/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient, times(2)).trackEvent(
        eq("incentives-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("incentives-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("A0010TZ", "STD:ENH")
          assertThat(it).containsEntry("A0030TZ", "STD:ENH")
        },
        isNull(),
      )
    }

    @Test
    fun `when initial prison count fails the whole report fails`() {
      nomisApi.stubGetActivePrisonersPageWithError(0, 500)

      webTestClient.put().uri("/incentives/reports/reconciliation")
        .exchange()
        .expectStatus().is5xxServerError
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() {
      nomisApi.stubGetActivePrisonersPageWithError(2, 500)

      webTestClient.put().uri("/incentives/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("incentives-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("incentives-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("A0010TZ", "STD:ENH")
          assertThat(it).containsEntry("A0020TZ", "STD:ENH")
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("incentives-reports-reconciliation-report"), any(), isNull()) }
  }
}
