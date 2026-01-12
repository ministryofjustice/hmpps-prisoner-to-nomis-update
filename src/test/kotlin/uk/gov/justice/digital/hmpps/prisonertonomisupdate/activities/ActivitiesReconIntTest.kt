package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import kotlin.collections.joinToString

class ActivitiesReconIntTest(
  @Autowired private val activitiesReconService: ActivitiesReconService,
) : IntegrationTestBase() {

  @Nested
  inner class AllocationReconciliationReport {
    @Nested
    inner class ReportRunsOk {
      @Test
      fun `should publish success telemetry if no differences`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1, offenderId = 1234))

        activitiesReconService.allocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-success",
            mapOf("prison" to "BXI"),
            null,
          )
        }
      }

      @Test
      fun `should publish failed telemetry if there are differences between NOMIS and DPS`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 2))

        activitiesReconService.allocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-allocation-reconciliation-report-failed"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                  "prison" to "BXI",
                  "type" to "different_count",
                  "bookingId" to "1234567",
                  "offenderNo" to "A1234AA",
                  "location" to "BXI",
                ),
              )
            },
            isNull(),
          )
        }
      }

      @Test
      fun `should publish failed telemetry for differences even if in different prison`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "OUT", nomisCount = 1, dpsCount = 2))

        activitiesReconService.allocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-allocation-reconciliation-report-failed"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                  "prison" to "BXI",
                  "type" to "different_count",
                  "bookingId" to "1234567",
                  "offenderNo" to "A1234AA",
                  "location" to "OUT",
                ),
              )
            },
            isNull(),
          )
        }
      }

      @Test
      fun `should publish telemetry for multiple prisons`() = runTest {
        stubGetPrisons("BXI", "MDI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1))
        stubBookingCounts("MDI", BookingDetailsStub(bookingId = 2345678, offenderNo = "A1234BB", location = "MDI", nomisCount = 1, dpsCount = 2))

        activitiesReconService.allocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI, MDI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-success",
            mapOf("prison" to "BXI"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-allocation-reconciliation-report-failed"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                  "prison" to "MDI",
                  "type" to "different_count",
                  "bookingId" to "2345678",
                  "offenderNo" to "A1234BB",
                  "location" to "MDI",
                ),
              )
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class ReportFailsDueToError {
      @Test
      fun `should publish error telemetry if fails to get prisons from NOMIS`() = runTest {
        nomisApi.stubGetServiceAgenciesWithError("ACTIVITY", 404)

        assertThrows<WebClientResponseException.NotFound> {
          activitiesReconService.allocationReconciliationReport()
        }

        await untilAsserted {
          verify(telemetryClient).trackEvent("activity-allocation-reconciliation-report-error", mapOf(), null)
        }
      }

      @Test
      fun `should publish error telemetry if fails to get recon data from NOMIS`() = runTest {
        stubGetPrisons("BXI")
        nomisApi.stubAllocationReconciliationWithError("BXI", 500)
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = null, dpsCount = 1))

        activitiesReconService.allocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-error",
            mapOf("prison" to "BXI"),
            null,
          )
        }
      }

      @Test
      fun `should publish error telemetry if fails to get recon data from DPS`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = null))
        activitiesApi.stubAllocationReconciliationWithError("BXI", 400)

        activitiesReconService.allocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-error",
            mapOf("prison" to "BXI"),
            null,
          )
        }
      }

      @Test
      fun `should continue to report on other prisons if one fails`() = runTest {
        stubGetPrisons("BXI", "MDI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = null))
        activitiesApi.stubAllocationReconciliationWithError("BXI", 400)
        stubBookingCounts("MDI", BookingDetailsStub(bookingId = 2345678, offenderNo = "A1234BB", location = "MDI", nomisCount = 1, dpsCount = 1))

        activitiesReconService.allocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI, MDI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-error",
            mapOf("prison" to "BXI"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-allocation-reconciliation-report-success",
            mapOf("prison" to "MDI"),
            null,
          )
        }
      }
    }

    private fun stubGetPrisons(vararg prisons: String) {
      val prisonsResponse = prisons.map { """{ "agencyId": "$it", "name": "$it" }""" }
      nomisApi.stubGetServiceAgencies("ACTIVITY", """ [ ${prisonsResponse.joinToString(",")} ] """)
    }

    private fun stubBookingCounts(prisonId: String, vararg bookingCounts: BookingDetailsStub) {
      bookingCounts
        .filterNot { it.nomisCount == null }
        .map { """{ "bookingId": ${it.bookingId}, "count": ${it.nomisCount} }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          nomisApi.stubAllocationReconciliation(
            prisonId,
            """ { "prisonId": "$prisonId", "bookings": [ ${this.joinToString(",")} ] } """,
          )
        }

      bookingCounts
        .filterNot { it.dpsCount == null }
        .map { """{ "bookingId": ${it.bookingId}, "count": ${it.dpsCount} }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          activitiesApi.stubAllocationReconciliation(
            prisonId,
            """ { "prisonCode": "$prisonId", "bookings": [ ${this.joinToString(",")} ] } """,
          )
        }

      bookingCounts
        .filterNot { it.nomisCount == it.dpsCount }
        .map { """{ "bookingId": ${it.bookingId}, "offenderNo": "${it.offenderNo}", "offenderId": "${it.offenderId}", "active": "${it.active}", "location": "${it.location}" }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          nomisApi.stubGetActivitiesPrisonerDetails(""" [ ${this.joinToString(",")} ] """)
        }
    }
  }

  @Nested
  inner class SuspendedAllocationReconciliationReport {

    @Nested
    inner class ReportRunsOk {
      @Test
      fun `should publish success telemetry if no differences`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1))

        activitiesReconService.suspendedAllocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-success",
            mapOf("prison" to "BXI"),
            null,
          )
        }
      }

      @Test
      fun `should publish failed telemetry if there are differences between NOMIS and DPS`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 2))

        activitiesReconService.suspendedAllocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-suspended-allocation-reconciliation-report-failed"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                  "prison" to "BXI",
                  "type" to "different_count",
                  "bookingId" to "1234567",
                  "offenderNo" to "A1234AA",
                  "location" to "BXI",
                ),
              )
            },
            isNull(),
          )
        }
      }

      @Test
      fun `should NOT publish failed telemetry in different prison`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "OUT", nomisCount = 1, dpsCount = 2))

        activitiesReconService.suspendedAllocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-suspended-allocation-reconciliation-report-success"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(mapOf("prison" to "BXI"))
            },
            isNull(),
          )
        }
      }

      @Test
      fun `should publish failed telemetry where prisoner not found in NOMIS`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "OUT", nomisCount = 1, dpsCount = 2))
        nomisApi.stubGetActivitiesPrisonerDetails("[]")

        activitiesReconService.suspendedAllocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-suspended-allocation-reconciliation-report-failed"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                  "prison" to "BXI",
                  "type" to "different_count",
                  "bookingId" to "1234567",
                  "offenderNo" to "Details not found in NOMIS",
                  "location" to "BXI",
                ),
              )
            },
            isNull(),
          )
        }
      }

      @Test
      fun `should publish telemetry for multiple prisons`() = runTest {
        stubGetPrisons("BXI", "MDI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1))
        stubBookingCounts("MDI", BookingDetailsStub(bookingId = 2345678, offenderNo = "A1234BB", location = "MDI", nomisCount = 1, dpsCount = 2))

        activitiesReconService.suspendedAllocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI, MDI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-success",
            mapOf("prison" to "BXI"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-suspended-allocation-reconciliation-report-failed"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                  "prison" to "MDI",
                  "type" to "different_count",
                  "bookingId" to "2345678",
                  "offenderNo" to "A1234BB",
                  "location" to "MDI",
                ),
              )
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class ReportFailsDueToError {
      @Test
      fun `should publish error telemetry if fails to get prisons from NOMIS`() = runTest {
        nomisApi.stubGetServiceAgenciesWithError("ACTIVITY", 404)

        assertThrows<WebClientResponseException> {
          activitiesReconService.suspendedAllocationReconciliationReport()
        }

        await untilAsserted {
          verify(telemetryClient).trackEvent("activity-suspended-allocation-reconciliation-report-error", mapOf(), null)
        }
      }

      @Test
      fun `should publish error telemetry if fails to get recon data from NOMIS`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = null, dpsCount = 1))
        nomisApi.stubSuspendedAllocationReconciliationWithError("BXI", 500)

        activitiesReconService.suspendedAllocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-error",
            mapOf("prison" to "BXI"),
            null,
          )
        }
      }

      @Test
      fun `should publish error telemetry if fails to get recon data from DPS`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = null))
        activitiesApi.stubSuspendedAllocationReconciliationWithError("BXI", 400)

        activitiesReconService.suspendedAllocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-error",
            mapOf("prison" to "BXI"),
            null,
          )
        }
      }

      @Test
      fun `should continue to report on other prisons if one fails`() = runTest {
        stubGetPrisons("BXI", "MDI")
        stubBookingCounts("BXI", BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = null))
        activitiesApi.stubSuspendedAllocationReconciliationWithError("BXI", 400)
        stubBookingCounts("MDI", BookingDetailsStub(bookingId = 2345678, offenderNo = "A1234BB", location = "MDI", nomisCount = 1, dpsCount = 1))

        activitiesReconService.suspendedAllocationReconciliationReport()

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-requested",
            mapOf("prisons" to "[BXI, MDI]"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-error",
            mapOf("prison" to "BXI"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-suspended-allocation-reconciliation-report-success",
            mapOf("prison" to "MDI"),
            null,
          )
        }
      }
    }

    private fun stubGetPrisons(vararg prisons: String) {
      val prisonsResponse = prisons.map { """{ "agencyId": "$it", "name": "$it" }""" }
      nomisApi.stubGetServiceAgencies("ACTIVITY", """ [ ${prisonsResponse.joinToString(",")} ] """)
    }

    private fun stubBookingCounts(prisonId: String, vararg bookingCounts: BookingDetailsStub) {
      bookingCounts
        .filterNot { it.nomisCount == null }
        .map { """{ "bookingId": ${it.bookingId}, "count": ${it.nomisCount} }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          nomisApi.stubSuspendedAllocationReconciliation(
            prisonId,
            """ { "prisonId": "$prisonId", "bookings": [ ${this.joinToString(",")} ] } """,
          )
        }

      bookingCounts
        .filterNot { it.dpsCount == null }
        .map { """{ "bookingId": ${it.bookingId}, "count": ${it.dpsCount} }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          activitiesApi.stubSuspendedAllocationReconciliation(
            prisonId,
            """ { "prisonCode": "$prisonId", "bookings": [ ${this.joinToString(",")} ] } """,
          )
        }

      bookingCounts
        .filterNot { it.nomisCount == it.dpsCount }
        .map { """{ "bookingId": ${it.bookingId}, "offenderNo": "${it.offenderNo}", "offenderId": "${it.offenderId}", "active": "${it.active}", "location": "${it.location}" }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          nomisApi.stubGetActivitiesPrisonerDetails(""" [ ${this.joinToString(",")} ] """)
        }
    }
  }

  @Nested
  inner class AttendanceReconciliationReport {
    private val today = LocalDate.now()

    @Nested
    inner class ReportRunsOk {
      @Test
      fun `should publish success telemetry if no differences`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", today, BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1))

        activitiesReconService.attendanceReconciliationReport(today)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]", "date" to "$today"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-success",
            mapOf("prison" to "BXI", "date" to "$today"),
            null,
          )
        }
      }

      @Test
      fun `should publish failed telemetry if there are differences between NOMIS and DPS`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", today, BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 2))

        activitiesReconService.attendanceReconciliationReport(today)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]", "date" to "$today"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-attendance-reconciliation-report-failed"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                  "prison" to "BXI",
                  "date" to "$today",
                  "type" to "different_count",
                  "bookingId" to "1234567",
                  "offenderNo" to "A1234AA",
                  "location" to "BXI",
                ),
              )
            },
            isNull(),
          )
        }
      }

      @Test
      fun `should publish telemetry for multiple prisons`() = runTest {
        stubGetPrisons("BXI", "MDI")
        stubBookingCounts("BXI", today, BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1))
        stubBookingCounts("MDI", today, BookingDetailsStub(bookingId = 2345678, offenderNo = "A1234BB", location = "MDI", nomisCount = 1, dpsCount = 2))

        activitiesReconService.attendanceReconciliationReport(today)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-requested",
            mapOf("prisons" to "[BXI, MDI]", "date" to "$today"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-success",
            mapOf("prison" to "BXI", "date" to "$today"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("activity-attendance-reconciliation-report-failed"),
            check {
              assertThat(it).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                  "prison" to "MDI",
                  "date" to "$today",
                  "type" to "different_count",
                  "bookingId" to "2345678",
                  "offenderNo" to "A1234BB",
                  "location" to "MDI",
                ),
              )
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class ReportFailsDueToError {

      @Test
      fun `should publish error telemetry if fails to get prisons from NOMIS`() = runTest {
        nomisApi.stubGetServiceAgenciesWithError("ACTIVITY", 404)

        assertThrows<WebClientResponseException> {
          activitiesReconService.attendanceReconciliationReport(today)
        }

        await untilAsserted {
          verify(telemetryClient).trackEvent("activity-attendance-reconciliation-report-error", mapOf("date" to "$today"), null)
        }
      }

      @Test
      fun `should publish error telemetry if fails to get recon data from NOMIS`() = runTest {
        stubGetPrisons("BXI")
        nomisApi.stubAttendanceReconciliationWithError("BXI", today, 500)
        stubBookingCounts("BXI", today, BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = null, dpsCount = 1))

        activitiesReconService.attendanceReconciliationReport(today)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]", "date" to "$today"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-error",
            mapOf("prison" to "BXI", "date" to "$today"),
            null,
          )
        }
      }

      @Test
      fun `should publish error telemetry if fails to get recon data from DPS`() = runTest {
        stubGetPrisons("BXI")
        stubBookingCounts("BXI", today, BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = null))
        activitiesApi.stubAttendanceReconciliationWithError("BXI", today, 400)

        activitiesReconService.attendanceReconciliationReport(today)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-requested",
            mapOf("prisons" to "[BXI]", "date" to "$today"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-error",
            mapOf("prison" to "BXI", "date" to "$today"),
            null,
          )
        }
      }

      @Test
      fun `should continue to report on other prisons if one fails`() = runTest {
        stubGetPrisons("BXI", "MDI")
        stubBookingCounts("BXI", today, BookingDetailsStub(bookingId = 1234567, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = null))
        activitiesApi.stubAttendanceReconciliationWithError("BXI", today, 400)
        stubBookingCounts("MDI", today, BookingDetailsStub(bookingId = 2345678, offenderNo = "A1234BB", location = "MDI", nomisCount = 1, dpsCount = 1))

        activitiesReconService.attendanceReconciliationReport(today)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-requested",
            mapOf("prisons" to "[BXI, MDI]", "date" to "$today"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-error",
            mapOf("prison" to "BXI", "date" to "$today"),
            null,
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            "activity-attendance-reconciliation-report-success",
            mapOf("prison" to "MDI", "date" to "$today"),
            null,
          )
        }
      }
    }

    private fun stubGetPrisons(vararg prisons: String) {
      val prisonsResponse = prisons.map { """{ "agencyId": "$it", "name": "$it" }""" }
      nomisApi.stubGetServiceAgencies("ACTIVITY", """ [ ${prisonsResponse.joinToString(",")} ] """)
    }

    private fun stubBookingCounts(prisonId: String, date: LocalDate, vararg bookingCounts: BookingDetailsStub) {
      bookingCounts
        .filterNot { it.nomisCount == null }
        .map { """{ "bookingId": ${it.bookingId}, "count": ${it.nomisCount} }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          nomisApi.stubAttendanceReconciliation(
            prisonId,
            date,
            """ { "prisonId": "$prisonId", "date": "$date", "bookings": [ ${this.joinToString(",")} ] } """,
          )
        }

      bookingCounts
        .filterNot { it.dpsCount == null }
        .map { """{ "bookingId": ${it.bookingId}, "count": ${it.dpsCount} }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          activitiesApi.stubAttendanceReconciliation(
            prisonId,
            date,
            """ { "prisonCode": "$prisonId", "date": "$date",  "bookings": [ ${this.joinToString(",")} ] } """,
          )
        }

      bookingCounts
        .filterNot { it.nomisCount == it.dpsCount }
        .map { """{ "bookingId": ${it.bookingId}, "offenderNo": "${it.offenderNo}", "offenderId": "${it.offenderId}", "active": "${it.active}", "location": "${it.location}" }""" }
        .takeIf { it.isNotEmpty() }
        ?.run {
          nomisApi.stubGetActivitiesPrisonerDetails(""" [ ${this.joinToString(",")} ] """)
        }
    }
  }
}
