@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerDetails
import java.time.LocalDate
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AllocationReconciliationResponse as DpsAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceReconciliationResponse as DpsAttendanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.BookingCount as DpsBookingCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AllocationReconciliationResponse as NomisAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AttendanceReconciliationResponse as NomisAttendanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCount as NomisBookingCount

class ActivitiesReconTest {

  private val telemetryClient: TelemetryClient = mock()
  private val nomisApiService: ActivitiesNomisApiService = mock()
  private val activitiesApiService: ActivitiesApiService = mock()
  private val activitiesReconService = ActivitiesReconService(telemetryClient, nomisApiService, activitiesApiService)

  @Nested
  inner class AllocationReconciliationReport {

    private val telemetryCaptor = argumentCaptor<Map<String, String>>()

    @Test
    fun `should publish success telemetry if no differences found`() = runTest {
      stubBookingCounts(prisonId = "BXI", BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "TRN", nomisCount = 1, dpsCount = 1))

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(nomisApiService).getAllocationReconciliation("BXI")
      verify(activitiesApiService).getAllocationReconciliation("BXI")
      verify(telemetryClient).trackEvent(
        "activity-allocation-reconciliation-report-success",
        mapOf("prison" to "BXI"),
        null,
      )
    }

    @Test
    fun `should publish fail telemetry for bookings found in Nomis only`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1),
        BookingDetailsStub(12, "A1234BB", "BXI", 1, null),
        BookingDetailsStub(13, "A1234CC", "BXI", 1, null),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verifyBlocking(telemetryClient, times(2)) {
        trackEvent(
          eq("activity-allocation-reconciliation-report-failed"),
          telemetryCaptor.capture(),
          isNull(),
        )
      }

      assertThat(telemetryCaptor.firstValue).containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "NOMIS_only",
          "bookingId" to "12",
          "offenderNo" to "A1234BB",
          "location" to "BXI",
        ),
      )
      assertThat(telemetryCaptor.secondValue).containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "NOMIS_only",
          "bookingId" to "13",
          "offenderNo" to "A1234CC",
          "location" to "BXI",
        ),
      )
    }

    @Test
    fun `should publish fail telemetry for bookings found in DPS only`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1),
        BookingDetailsStub(12, "A1234BB", "BXI", null, 1),
        BookingDetailsStub(13, "A1234CC", "BXI", null, 1),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient, times(2)).trackEvent(
        eq("activity-allocation-reconciliation-report-failed"),
        telemetryCaptor.capture(),
        isNull(),
      )

      assertThat(telemetryCaptor.firstValue).containsAllEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "DPS_only",
          "bookingId" to "12",
          "offenderNo" to "A1234BB",
          "location" to "BXI",
        ),
      )
      assertThat(telemetryCaptor.secondValue).containsAllEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "DPS_only",
          "bookingId" to "13",
          "offenderNo" to "A1234CC",
          "location" to "BXI",
        ),
      )
    }

    @Test
    fun `should publish fail telemetry for bookings with different counts in NOMIS and DPS`() = runTest {
      stubBookingCounts(prisonId = "BXI", BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 2))

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-reconciliation-report-failed"),
        telemetryCaptor.capture(),
        isNull(),
      )

      assertThat(telemetryCaptor.firstValue).containsAllEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "different_count",
          "bookingId" to "11",
          "offenderNo" to "A1234AA",
          "location" to "BXI",
        ),
      )
    }

    @Test
    fun `should publish fail telemetry for bookings with a combination of different reasons`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1),
        BookingDetailsStub(12, "A1234BB", "BXI", 1, null),
        BookingDetailsStub(13, "A1234CC", "BXI", null, 1),
        BookingDetailsStub(14, "A1234DD", "BXI", 1, 1),
        BookingDetailsStub(15, "A1234EE", "BXI", 1, 2),
        BookingDetailsStub(16, "A1234FF", "BXI", 1, 1),
        BookingDetailsStub(17, "A1234GG", "BXI", 1, null),
        BookingDetailsStub(18, "A1234HH", "BXI", null, 1),
        BookingDetailsStub(19, "A1234II", "BXI", 1, 2),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient, times(6)).trackEvent(
        eq("activity-allocation-reconciliation-report-failed"),
        telemetryCaptor.capture(),
        isNull(),
      )

      assertThat(telemetryCaptor.allValues).extracting("bookingId", "type").containsExactly(
        tuple("12", "NOMIS_only"),
        tuple("13", "DPS_only"),
        tuple("15", "different_count"),
        tuple("17", "NOMIS_only"),
        tuple("18", "DPS_only"),
        tuple("19", "different_count"),
      )
    }

    @Test
    fun `should publish fail telemetry for bookings that are not in order`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingDetailsStub(bookingId = 19, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 2),
        BookingDetailsStub(18, "A1234BB", "BXI", null, 1),
        BookingDetailsStub(17, "A1234CC", "BXI", 1, null),
        BookingDetailsStub(16, "A1234DD", "BXI", 1, 1),
        BookingDetailsStub(15, "A1234EE", "BXI", 1, 2),
        BookingDetailsStub(14, "A1234FF", "BXI", 1, 1),
        BookingDetailsStub(13, "A1234GG", "BXI", null, 1),
        BookingDetailsStub(12, "A1234HH", "BXI", 1, null),
        BookingDetailsStub(11, "A1234II", "BXI", 1, 1),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient, times(6)).trackEvent(
        eq("activity-allocation-reconciliation-report-failed"),
        telemetryCaptor.capture(),
        isNull(),
      )

      assertThat(telemetryCaptor.allValues).extracting("bookingId", "type").containsExactly(
        tuple("12", "NOMIS_only"),
        tuple("13", "DPS_only"),
        tuple("15", "different_count"),
        tuple("17", "NOMIS_only"),
        tuple("18", "DPS_only"),
        tuple("19", "different_count"),
      )
    }

    @Test
    fun `should publish fail telemetry if a prisoner is now in a different location`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "OUT", nomisCount = 1, dpsCount = 2),
        BookingDetailsStub(bookingId = 12, offenderNo = "A1234BB", location = "BXI", nomisCount = 1, dpsCount = 2),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verifyBlocking(telemetryClient, times(2)) {
        trackEvent(
          eq("activity-allocation-reconciliation-report-failed"),
          telemetryCaptor.capture(),
          isNull(),
        )
      }

      assertThat(telemetryCaptor.allValues.size).isEqualTo(2)
      assertThat(telemetryCaptor.firstValue).containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "different_count",
          "bookingId" to "11",
          "offenderNo" to "A1234AA",
          "location" to "OUT",
        ),
      )
      assertThat(telemetryCaptor.secondValue).containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "different_count",
          "bookingId" to "12",
          "offenderNo" to "A1234BB",
          "location" to "BXI",
        ),
      )
    }

    @Test
    fun `should not publish success telemetry if only failures are for prisoners different locations`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "OUT", nomisCount = 1, dpsCount = 2),
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234BB", location = "MDI", nomisCount = 1, dpsCount = 2),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verifyBlocking(telemetryClient, times(2)) {
        trackEvent(
          eq("activity-allocation-reconciliation-report-failed"),
          any(),
          isNull(),
        )
      }
    }

    @Test
    fun `should publish error telemetry`() = runTest {
      stubBookingCounts(prisonId = "BXI", BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "TRN", nomisCount = 1, dpsCount = 1))
      whenever(nomisApiService.getAllocationReconciliation(anyString()))
        .thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient).trackEvent(
        "activity-allocation-reconciliation-report-error",
        mapOf("prison" to "BXI"),
        null,
      )
    }

    private fun stubBookingCounts(prisonId: String, vararg bookingCounts: BookingDetailsStub) = runTest {
      // stub the booking allocations counts for NOMIS
      bookingCounts
        .filterNot { it.nomisCount == null }
        .map { NomisBookingCount(it.bookingId, it.nomisCount!!) }
        .also {
          whenever(nomisApiService.getAllocationReconciliation(anyString()))
            .thenReturn(NomisAllocationResponse(prisonId, it))
        }

      // stub the booking allocations counts for DPS
      bookingCounts
        .filterNot { it.dpsCount == null }
        .map { DpsBookingCount(it.bookingId, it.dpsCount!!) }
        .also {
          whenever(activitiesApiService.getAllocationReconciliation(anyString()))
            .thenReturn(DpsAllocationResponse(prisonId, it))
        }

      // stub the booking details calls for all will be reported on (that don't match counts)
      bookingCounts
        .filterNot { it.nomisCount == it.dpsCount }
        .map { PrisonerDetails(it.offenderNo, 0, it.bookingId, it.location, true, null) }
        .also { whenever(nomisApiService.getPrisonerDetails(any())).thenReturn(it) }
    }
  }

  @Nested
  inner class AttendanceReconciliationReport {

    private val telemetryCaptor = argumentCaptor<Map<String, String>>()
    private val today = LocalDate.now()

    @Test
    fun `should publish success telemetry`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        date = today,
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "TRN", nomisCount = 1, dpsCount = 1),
      )

      activitiesReconService.attendancesReconciliationReport("BXI", today)

      verify(nomisApiService).getAttendanceReconciliation("BXI", today)
      verify(activitiesApiService).getAttendanceReconciliation("BXI", today)
      verify(telemetryClient).trackEvent(
        "activity-attendance-reconciliation-report-success",
        mapOf("prison" to "BXI", "date" to "$today"),
        null,
      )
    }

    @Test
    fun `should publish fail telemetry`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        date = today,
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1),
        BookingDetailsStub(12, "A1234BB", "BXI", 1, null),
      )

      activitiesReconService.attendancesReconciliationReport("BXI", today)

      verifyBlocking(telemetryClient) {
        trackEvent(
          eq("activity-attendance-reconciliation-report-failed"),
          telemetryCaptor.capture(),
          isNull(),
        )
      }

      assertThat(telemetryCaptor.firstValue).containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "prison" to "BXI",
          "date" to "$today",
          "type" to "NOMIS_only",
          "bookingId" to "12",
          "offenderNo" to "A1234BB",
          "location" to "BXI",
        ),
      )
    }

    @Test
    fun `should publish fail telemetry for various types of failure`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        date = today,
        BookingDetailsStub(bookingId = 19, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 2),
        BookingDetailsStub(18, "A1234BB", "BXI", null, 1),
        BookingDetailsStub(17, "A1234CC", "BXI", 1, null),
        BookingDetailsStub(16, "A1234DD", "BXI", 1, 1),
        BookingDetailsStub(15, "A1234EE", "BXI", 1, 2),
        BookingDetailsStub(14, "A1234FF", "BXI", 1, 1),
        BookingDetailsStub(13, "A1234GG", "BXI", null, 1),
        BookingDetailsStub(12, "A1234HH", "BXI", 1, null),
        BookingDetailsStub(11, "A1234II", "BXI", 1, 1),
      )

      activitiesReconService.attendancesReconciliationReport("BXI", today)

      verify(telemetryClient, times(6)).trackEvent(
        eq("activity-attendance-reconciliation-report-failed"),
        telemetryCaptor.capture(),
        isNull(),
      )

      assertThat(telemetryCaptor.allValues).extracting("bookingId", "type").containsExactly(
        tuple("12", "NOMIS_only"),
        tuple("13", "DPS_only"),
        tuple("15", "different_count"),
        tuple("17", "NOMIS_only"),
        tuple("18", "DPS_only"),
        tuple("19", "different_count"),
      )
    }

    @Test
    fun `should NOT publish fail telemetry if prisoner in a different location`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        date = today,
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "OUT", nomisCount = 1, dpsCount = 2),
        BookingDetailsStub(bookingId = 12, offenderNo = "A1234BB", location = "BXI", nomisCount = 1, dpsCount = 2),
      )

      activitiesReconService.attendancesReconciliationReport("BXI", today)

      verifyBlocking(telemetryClient, times(1)) {
        trackEvent(
          eq("activity-attendance-reconciliation-report-failed"),
          telemetryCaptor.capture(),
          isNull(),
        )
      }

      assertThat(telemetryCaptor.allValues.size).isEqualTo(1)
      assertThat(telemetryCaptor.firstValue).containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "prison" to "BXI",
          "date" to "$today",
          "type" to "different_count",
          "bookingId" to "12",
          "offenderNo" to "A1234BB",
          "location" to "BXI",
        ),
      )
    }

    @Test
    fun `should still publish success telemetry if only differences are for prisoners in a different location`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        date = today,
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "OUT", nomisCount = 1, dpsCount = 2),
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234BB", location = "MDI", nomisCount = 1, dpsCount = 2),
      )

      activitiesReconService.attendancesReconciliationReport("BXI", today)

      verifyBlocking(telemetryClient) {
        trackEvent(
          eq("activity-attendance-reconciliation-report-success"),
          any(),
          isNull(),
        )
      }
    }

    @Test
    fun `should publish error telemetry`() = runTest {
      stubBookingCounts(prisonId = "BXI", today, BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "TRN", nomisCount = 1, dpsCount = 1))
      whenever(nomisApiService.getAttendanceReconciliation(anyString(), any()))
        .thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      activitiesReconService.attendancesReconciliationReport("BXI", today)

      verify(telemetryClient).trackEvent(
        "activity-attendance-reconciliation-report-error",
        mapOf("prison" to "BXI", "date" to "$today"),
        null,
      )
    }

    private fun stubBookingCounts(prisonId: String, date: LocalDate, vararg bookingCounts: BookingDetailsStub) = runTest {
      // stub the booking attendance counts for NOMIS
      bookingCounts
        .filterNot { it.nomisCount == null }
        .map { NomisBookingCount(it.bookingId, it.nomisCount!!) }
        .also {
          whenever(nomisApiService.getAttendanceReconciliation(anyString(), any()))
            .thenReturn(NomisAttendanceResponse(prisonId, date, it))
        }

      // stub the booking attendance counts for DPS
      bookingCounts
        .filterNot { it.dpsCount == null }
        .map { DpsBookingCount(it.bookingId, it.dpsCount!!) }
        .also {
          whenever(activitiesApiService.getAttendanceReconciliation(anyString(), any()))
            .thenReturn(DpsAttendanceResponse(prisonId, date, it))
        }

      // stub the booking details calls for all will be reported on (that don't match counts)
      bookingCounts
        .filterNot { it.nomisCount == it.dpsCount }
        .map { PrisonerDetails(it.offenderNo, 0, it.bookingId, it.location, true, null) }
        .also { whenever(nomisApiService.getPrisonerDetails(any())).thenReturn(it) }
    }
  }
}

internal data class BookingDetailsStub(val bookingId: Long, val offenderNo: String, val location: String, val nomisCount: Long?, val dpsCount: Long?, val offenderId: Long = 1234, val active: Boolean = true)
