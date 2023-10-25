@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AllocationReconciliationResponse as DpsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.BookingCount as DpsBookingCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AllocationReconciliationResponse as NomisResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.BookingCount as NomisBookingCount

class ActivitiesReconTest {

  private val telemetryClient: TelemetryClient = mock()
  private val nomisApiService: NomisApiService = mock()
  private val reportScope: CoroutineScope = mock()
  private val activitiesApiService: ActivitiesApiService = mock()
  private val activitiesReconService = ActivitiesReconService(telemetryClient, nomisApiService, reportScope, activitiesApiService)

  @Nested
  inner class AllocationReconciliationReport {

    // Using check when the call being verified is inside a coroutine doesn't bubble the errors up to fail the test - so we use a captor instead
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
        BookingDetailsStub(12, "A1234BB", "TRN", 1, null),
        BookingDetailsStub(13, "A1234CC", "OUT", 1, null),
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
          "location" to "TRN",
        ),
      )
      assertThat(telemetryCaptor.secondValue).containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "NOMIS_only",
          "bookingId" to "13",
          "offenderNo" to "A1234CC",
          "location" to "OUT",
        ),
      )
    }

    @Test
    fun `should publish fail telemetry for bookings found in DPS only`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1),
        BookingDetailsStub(12, "A1234BB", "TRN", null, 1),
        BookingDetailsStub(13, "A1234CC", "OUT", null, 1),
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
          "location" to "TRN",
        ),
      )
      assertThat(telemetryCaptor.secondValue).containsAllEntriesOf(
        mapOf(
          "prison" to "BXI",
          "type" to "DPS_only",
          "bookingId" to "13",
          "offenderNo" to "A1234CC",
          "location" to "OUT",
        ),
      )
    }

    @Test
    fun `should publish fail telemetry for bookings with different counts in NOMIS and DPS`() = runTest {
      stubBookingCounts(prisonId = "BXI", BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "TRN", nomisCount = 1, dpsCount = 2))

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
          "location" to "TRN",
        ),
      )
    }

    @Test
    fun `should publish fail telemetry for bookings with a combination of different reasons`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingDetailsStub(bookingId = 11, offenderNo = "A1234AA", location = "BXI", nomisCount = 1, dpsCount = 1),
        BookingDetailsStub(12, "A1234BB", "TRN", 1, null),
        BookingDetailsStub(13, "A1234CC", "OUT", null, 1),
        BookingDetailsStub(14, "A1234DD", "BXI", 1, 1),
        BookingDetailsStub(15, "A1234EE", "TRN", 1, 2),
        BookingDetailsStub(16, "A1234FF", "OUT", 1, 1),
        BookingDetailsStub(17, "A1234GG", "BXI", 1, null),
        BookingDetailsStub(18, "A1234HH", "TRN", null, 1),
        BookingDetailsStub(19, "A1234II", "OUT", 1, 2),
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
        BookingDetailsStub(18, "A1234BB", "TRN", null, 1),
        BookingDetailsStub(17, "A1234CC", "OUT", 1, null),
        BookingDetailsStub(16, "A1234DD", "BXI", 1, 1),
        BookingDetailsStub(15, "A1234EE", "TRN", 1, 2),
        BookingDetailsStub(14, "A1234FF", "OUT", 1, 1),
        BookingDetailsStub(13, "A1234GG", "BCI", null, 1),
        BookingDetailsStub(12, "A1234HH", "TRN", 1, null),
        BookingDetailsStub(11, "A1234II", "OUT", 1, 1),
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

    private fun stubBookingCounts(prisonId: String, vararg bookingCounts: BookingDetailsStub) = runTest {
      // stub the booking allocations counts for NOMIS
      bookingCounts
        .filterNot { it.nomisCount == null }
        .map { NomisBookingCount(it.bookingId, it.nomisCount!!) }
        .also {
          whenever(nomisApiService.getAllocationReconciliation(anyString()))
            .thenReturn(NomisResponse(prisonId, it))
        }

      // stub the booking allocations counts for DPS
      bookingCounts
        .filterNot { it.dpsCount == null }
        .map { DpsBookingCount(it.bookingId, it.dpsCount!!) }
        .also {
          whenever(activitiesApiService.getAllocationReconciliation(anyString()))
            .thenReturn(DpsResponse(prisonId, it))
        }

      // stub the booking details calls for all will be reported on (that don't match counts)
      bookingCounts
        .filterNot { it.nomisCount == it.dpsCount }
        .map { PrisonerDetails(it.offenderNo, it.bookingId, it.location) }
        .also { whenever(nomisApiService.getPrisonerDetails(any())).thenReturn(it) }
    }
  }
}

internal data class BookingDetailsStub(val bookingId: Long, val offenderNo: String, val location: String, val nomisCount: Long?, val dpsCount: Long?)
