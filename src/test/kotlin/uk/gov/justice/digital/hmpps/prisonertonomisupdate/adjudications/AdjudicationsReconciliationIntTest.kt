package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

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
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.lang.RuntimeException

@ExtendWith(MockitoExtension::class)
class AdjudicationsReconciliationIntTest(
  @Autowired private val adjudicationsReconService: AdjudicationsReconciliationService,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @DisplayName("Adjudications reconciliation report")
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
            offenderNo = generateOffenderNo(prefix = "A", sequence = it, suffix = "TZ"),
            adaSummaries = listOf(nomisSummary(days = 10)),
          ),
        )
      }

      nomisApi.stubGetMergesFromDate("A0001TZ")
      nomisApi.stubGetMergesFromDate("A0034TZ")
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      adjudicationsReconService.generateAdjudicationsReconciliationReport()

      verify(telemetryClient).trackEvent(eq("adjudication-reports-reconciliation-requested"), check { assertThat(it).containsEntry("active-prisoners", "34") }, isNull())

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() = runTest {
      adjudicationsReconService.generateAdjudicationsReconciliationReport()

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
      adjudicationsReconService.generateAdjudicationsReconciliationReport()

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
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      adjudicationsApiServer.stubGetAdjudicationsByBookingIdWithError(1, 500)

      adjudicationsReconService.generateAdjudicationsReconciliationReport()

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
    fun `when initial prison count fails the whole report fails`() = runTest {
      nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 0, responseCode = 500)

      assertThrows<RuntimeException> {
        adjudicationsReconService.generateAdjudicationsReconciliationReport()
      }
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() = runTest {
      nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 2, responseCode = 500)

      adjudicationsReconService.generateAdjudicationsReconciliationReport()

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
