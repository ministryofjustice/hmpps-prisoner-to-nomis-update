package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

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
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsDpsApiExtension.Companion.alertsDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.lang.RuntimeException

@ExtendWith(MockitoExtension::class)
class AlertsReconciliationIntTest(
  @Autowired private val alertsReconciliationService: AlertsReconciliationService,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @Autowired
  private lateinit var alertsNomisApi: AlertsNomisApiMockServer

  @DisplayName("Alerts reconciliation report")
  @Nested
  inner class GenerateAlertsReconciliationReport {
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
      alertsDpsApi.stubGetActiveAlertsForPrisoner(
        "A0001TZ",
        dpsAlert().copy(alertCode = dpsAlertCode("HPI")),
        dpsAlert().copy(alertCode = dpsAlertCode("XA")),
      )
      alertsNomisApi.stubGetAlertsForReconciliation(
        "A0001TZ",
        response = PrisonerAlertsResponse(
          latestBookingAlerts = listOf(alertResponse().copy(alertCode = alertCode("HPI"))),
        ),
      )

      alertsDpsApi.stubGetActiveAlertsForPrisoner(
        "A0002TZ",
        dpsAlert().copy(alertCode = dpsAlertCode("HPI")),
        dpsAlert().copy(alertCode = dpsAlertCode("XA")),
      )
      alertsNomisApi.stubGetAlertsForReconciliation(
        "A0002TZ",
        response = PrisonerAlertsResponse(
          latestBookingAlerts = listOf(alertResponse().copy(alertCode = alertCode("HPI"))),
        ),
      )

      alertsDpsApi.stubGetActiveAlertsForPrisoner(
        "A0034TZ",
        dpsAlert().copy(alertCode = dpsAlertCode("HPI")),
        dpsAlert().copy(alertCode = dpsAlertCode("HA1")),
      )
      alertsNomisApi.stubGetAlertsForReconciliation(
        "A0034TZ",
        response = PrisonerAlertsResponse(
          latestBookingAlerts = listOf(
            alertResponse().copy(alertCode = alertCode("HPI")),
            alertResponse().copy(alertCode = alertCode("HA2")),
            alertResponse().copy(alertCode = alertCode("XA")),
          ),
        ),
      )

      // all others have alerts
      (3..<numberOfActivePrisoners).forEach {
        val offenderNo = generateOffenderNo(sequence = it)
        alertsDpsApi.stubGetActiveAlertsForPrisoner(
          offenderNo,
          dpsAlert().copy(alertCode = dpsAlertCode("HPI")),
        )
        alertsNomisApi.stubGetAlertsForReconciliation(
          offenderNo,
          response = PrisonerAlertsResponse(
            latestBookingAlerts = listOf(alertResponse().copy(alertCode = alertCode("HPI"))),
          ),
        )
      }
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      alertsReconciliationService.generateAlertsReconciliationReport()

      verify(telemetryClient).trackEvent(eq("alerts-reports-reconciliation-requested"), check { assertThat(it).containsEntry("active-prisoners", "34") }, isNull())

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() = runTest {
      alertsReconciliationService.generateAlertsReconciliationReport()

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
      alertsReconciliationService.generateAlertsReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("alerts-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
          assertThat(it).containsEntry("A0001TZ", "missing-dps=0:missing-nomis=1")
          assertThat(it).containsEntry("A0002TZ", "missing-dps=0:missing-nomis=1")
          assertThat(it).containsEntry("A0034TZ", "missing-dps=2:missing-nomis=1")
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("alerts-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      val mismatchedRecords = telemetryCaptor.allValues.map { it["offenderNo"] }

      assertThat(mismatchedRecords).containsOnly("A0001TZ", "A0002TZ", "A0034TZ")
      with(telemetryCaptor.allValues.find { it["offenderNo"] == "A0001TZ" }) {
        assertThat(this).containsEntry("bookingId", "1")
        assertThat(this).containsEntry("missingFromDps", "")
        assertThat(this).containsEntry("missingFromNomis", "XA")
      }
      with(telemetryCaptor.allValues.find { it["offenderNo"] == "A0002TZ" }) {
        assertThat(this).containsEntry("bookingId", "2")
        assertThat(this).containsEntry("missingFromDps", "")
        assertThat(this).containsEntry("missingFromNomis", "XA")
      }
      with(telemetryCaptor.allValues.find { it["offenderNo"] == "A0034TZ" }) {
        assertThat(this).containsEntry("bookingId", "34")
        assertThat(this).containsEntry("missingFromDps", "HA2, XA")
        assertThat(this).containsEntry("missingFromNomis", "HA1")
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      alertsDpsApi.stubGetActiveAlertsForPrisoner("A0002TZ", HttpStatus.INTERNAL_SERVER_ERROR)

      alertsReconciliationService.generateAlertsReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(1)).trackEvent(
        eq("alerts-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("alerts-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsKeys("A0001TZ", "A0034TZ")
        },
        isNull(),
      )
    }

    @Test
    fun `when initial prison count fails the whole report fails`() = runTest {
      nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 0, responseCode = 500)

      assertThrows<RuntimeException> {
        alertsReconciliationService.generateAlertsReconciliationReport()
      }
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() = runTest {
      nomisApi.stubGetActivePrisonersPageWithError(pageNumber = 2, responseCode = 500)

      alertsReconciliationService.generateAlertsReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("alerts-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("alerts-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
          assertThat(it).containsKeys("A0001TZ", "A0002TZ", "A0034TZ")
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("alerts-reports-reconciliation-report"), any(), isNull()) }
  }
}
