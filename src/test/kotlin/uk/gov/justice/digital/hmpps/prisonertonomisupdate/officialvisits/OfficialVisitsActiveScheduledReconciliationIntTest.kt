package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.time.LocalDate
import java.time.LocalDateTime

class OfficialVisitsActiveScheduledReconciliationIntTest(
  @Autowired private val reconciliationService: OfficialVisitsActiveScheduledReconciliationService,
  @Autowired private val nomisApi: OfficialVisitsNomisApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @DisplayName("Official visits active scheduled reconciliation report")
  @Nested
  inner class GenerateActiveScheduledVisitsReconciliationReportBatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      NomisApiExtension.nomisApi.stuGetAllLatestBookings(
        response = BookingIdsWithLast(
          lastBookingId = 0,
          prisonerIds = (1L..3).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )

      // both same
      nomisApi.stubGetOfficialVisitsForPrisoner(offenderNo = "A0001TZ", response = listOf(officialVisitResponse().copy(visitId = 1, offenderNo = "A0001TZ"), officialVisitResponse().copy(visitId = 2, offenderNo = "A0001TZ")))
      dpsApi.stubGetOfficialVisitsForPrisoner(offenderNo = "A0001TZ", response = listOf(syncOfficialVisit().copy(officialVisitId = 1, prisonerNumber = "A0001TZ"), syncOfficialVisit().copy(officialVisitId = 2, prisonerNumber = "A0001TZ")))

      // one missing
      nomisApi.stubGetOfficialVisitsForPrisoner(offenderNo = "A0002TZ", response = listOf(officialVisitResponse().copy(visitId = 3, offenderNo = "A0002TZ", startDateTime = LocalDateTime.parse("2020-01-02T10:30"))))
      dpsApi.stubGetOfficialVisitsForPrisoner(offenderNo = "A0002TZ", response = listOf(syncOfficialVisit().copy(officialVisitId = 3, prisonerNumber = "A0002TZ", startTime = "10:30", visitDate = LocalDate.parse("2020-01-02")), syncOfficialVisit().copy(officialVisitId = 99, prisonerNumber = "A0002TZ")))

      // one different
      nomisApi.stubGetOfficialVisitsForPrisoner(offenderNo = "A0003TZ", response = listOf(officialVisitResponse().copy(visitId = 4, offenderNo = "A0003TZ", startDateTime = LocalDateTime.parse("2020-01-01T17:01")), officialVisitResponse().copy(visitId = 5, offenderNo = "A0003TZ")))
      dpsApi.stubGetOfficialVisitsForPrisoner(offenderNo = "A0003TZ", response = listOf(syncOfficialVisit().copy(officialVisitId = 4, prisonerNumber = "A0003TZ", startTime = "17:00", visitDate = LocalDate.parse("2020-01-01")), syncOfficialVisit().copy(officialVisitId = 5, prisonerNumber = "A0003TZ")))
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      reconciliationService.generateActiveScheduledVisitsReconciliationReportBatch()

      verify(telemetryClient).trackEvent(
        eq("official-visits-active-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output report`() = runTest {
      reconciliationService.generateActiveScheduledVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-active-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("pages-count", "1")
          assertThat(it).containsEntry("prisoners-count", "3")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch for number of visits`() = runTest {
      reconciliationService.generateActiveScheduledVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-active-reconciliation-mismatch"),
        eq(
          mapOf(
            "dpsCount" to "2",
            "nomisCount" to "1",
            "offenderNo" to "A0002TZ",
            "reason" to "visit-record-missing",
          ),
        ),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("official-visits-active-reconciliation-mismatch"),
        eq(
          mapOf(
            "startDateTime" to "2020-01-01T10:00",
            "offenderNo" to "A0002TZ",
            "reason" to "dps-visit-not-found",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch for different visit details`() = runTest {
      reconciliationService.generateActiveScheduledVisitsReconciliationReportBatch()
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("official-visits-active-reconciliation-mismatch"),
        eq(
          mapOf(
            "startDateTime" to "2020-01-01T17:00",
            "offenderNo" to "A0003TZ",
            "reason" to "dps-visit-not-found",
          ),
        ),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("official-visits-active-reconciliation-mismatch"),
        eq(
          mapOf(
            "startDateTime" to "2020-01-01T17:01",
            "offenderNo" to "A0003TZ",
            "reason" to "nomis-visit-not-found",
          ),
        ),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("official-visits-active-reconciliation-report"), any(), isNull()) }
    }
  }
}
