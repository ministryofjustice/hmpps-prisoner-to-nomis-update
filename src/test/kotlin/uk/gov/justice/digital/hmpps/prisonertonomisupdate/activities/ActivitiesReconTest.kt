@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    @Test
    fun `should publish success telemetry if no differences found`() = runTest {
      stubBookingCounts(prisonId = "BXI", BookingCountStub(bookingId = 11, nomisCount = 1, dpsCount = 1))

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
        BookingCountStub(bookingId = 11, nomisCount = 1, dpsCount = 1),
        BookingCountStub(12, 1, null),
        BookingCountStub(13, 1, null),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient).trackEvent(
        "activity-allocation-reconciliation-report-failed",
        mapOf("prison" to "BXI", "NOMIS_only" to "[12, 13]"),
        null,
      )
    }

    @Test
    fun `should publish fail telemetry for bookings found in DPS only`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingCountStub(bookingId = 11, nomisCount = 1, dpsCount = 1),
        BookingCountStub(12, null, 1),
        BookingCountStub(13, null, 1),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient).trackEvent(
        "activity-allocation-reconciliation-report-failed",
        mapOf("prison" to "BXI", "DPS_only" to "[12, 13]"),
        null,
      )
    }

    @Test
    fun `should publish fail telemetry for bookings with different counts in NOMIS and DPS`() = runTest {
      stubBookingCounts(prisonId = "BXI", BookingCountStub(bookingId = 11, nomisCount = 1, dpsCount = 2))

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient).trackEvent(
        "activity-allocation-reconciliation-report-failed",
        mapOf("prison" to "BXI", "different_count" to "[11]"),
        null,
      )
    }

    @Test
    fun `should publish fail telemetry for bookings with a combination of different reasons`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingCountStub(bookingId = 11, nomisCount = 1, dpsCount = 1),
        BookingCountStub(12, 1, null),
        BookingCountStub(13, null, 1),
        BookingCountStub(14, 1, 1),
        BookingCountStub(15, 1, 2),
        BookingCountStub(16, 1, 1),
        BookingCountStub(17, 1, null),
        BookingCountStub(18, null, 1),
        BookingCountStub(19, 1, 2),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient).trackEvent(
        "activity-allocation-reconciliation-report-failed",
        mapOf(
          "prison" to "BXI",
          "NOMIS_only" to "[12, 17]",
          "DPS_only" to "[13, 18]",
          "different_count" to "[15, 19]",
        ),
        null,
      )
    }

    @Test
    fun `should publish fail telemetry for bookings that are not in order`() = runTest {
      stubBookingCounts(
        prisonId = "BXI",
        BookingCountStub(bookingId = 19, nomisCount = 1, dpsCount = 2),
        BookingCountStub(18, null, 1),
        BookingCountStub(17, 1, null),
        BookingCountStub(16, 1, 1),
        BookingCountStub(15, 1, 2),
        BookingCountStub(14, 1, 1),
        BookingCountStub(13, null, 1),
        BookingCountStub(12, 1, null),
        BookingCountStub(11, 1, 1),
      )

      activitiesReconService.allocationsReconciliationReport("BXI")

      verify(telemetryClient).trackEvent(
        "activity-allocation-reconciliation-report-failed",
        mapOf(
          "prison" to "BXI",
          "NOMIS_only" to "[12, 17]",
          "DPS_only" to "[13, 18]",
          "different_count" to "[15, 19]",
        ),
        null,
      )
    }

    private fun stubBookingCounts(prisonId: String, vararg bookingCounts: BookingCountStub) = runTest {
      bookingCounts
        .filterNot { it.nomisCount == null }
        .map { NomisBookingCount(it.bookingId, it.nomisCount!!) }
        .also {
          whenever(nomisApiService.getAllocationReconciliation(anyString()))
            .thenReturn(NomisResponse(prisonId, it))
        }

      bookingCounts
        .filterNot { it.dpsCount == null }
        .map { DpsBookingCount(it.bookingId, it.dpsCount!!) }
        .also {
          whenever(activitiesApiService.getAllocationReconciliation(anyString()))
            .thenReturn(DpsResponse(prisonId, it))
        }
    }
  }
}

internal data class BookingCountStub(val bookingId: Long, val nomisCount: Long?, val dpsCount: Long?)
