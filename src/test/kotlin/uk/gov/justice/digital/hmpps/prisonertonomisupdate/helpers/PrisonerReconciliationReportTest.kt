package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection.ACTIVE_PRISONERS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection.ALL_PRISONERS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerNosWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

class PrisonerReconciliationReportTest {
  private val telemetryClient: TelemetryClient = mock()
  private val nomisApi: NomisApiService = mock()
  private val names = ReconciliationNames("test-reconciliation", "Test")
  private var mismatchesFor: (String) -> List<String> = { emptyList() }

  private fun report(selection: PrisonerSelection, pageSize: Int = 10) = PrisonerReconciliationReport(telemetryClient, nomisApi, names, selection, pageSize = pageSize, threadCount = 2) { mismatchesFor(it) }

  private suspend fun stubAllPrisoners(vararg offenderNos: String) = whenever(nomisApi.getAllPrisoners(any(), any()))
    .thenReturn(PrisonerNosWithLast(prisonerIds = offenderNos.map { PrisonerId(it) }, lastOffenderId = 100))

  private suspend fun stubActiveBookings(vararg offenderNos: String) = whenever(nomisApi.getAllLatestBookings(any(), any(), any()))
    .thenReturn(BookingIdsWithLast(prisonerIds = offenderNos.mapIndexed { i, it -> PrisonerIds(i + 1L, it) }, lastBookingId = 100))

  @Nested
  inner class Selection {
    @Test
    fun `all prisoners requests all prisoners from NOMIS and reports the selection as the type`() = runTest {
      stubAllPrisoners("A0001TZ")

      report(ALL_PRISONERS).run()

      verify(telemetryClient).trackEvent(eq("test-reconciliation-requested"), eq(mapOf("TYPE" to "ALL_PRISONERS")), isNull())
      verify(telemetryClient).trackEvent(eq("test-reconciliation-report"), check { assertThat(it).containsEntry("TYPE", "ALL_PRISONERS") }, isNull())
    }

    @Test
    fun `active prisoners requests only active bookings from NOMIS and reports the selection as the type`() = runTest {
      stubActiveBookings("A0001TZ")

      report(ACTIVE_PRISONERS).run()

      verify(nomisApi).getAllLatestBookings(activeOnly = true, lastBookingId = 0, pageSize = 10)
      verify(telemetryClient).trackEvent(eq("test-reconciliation-requested"), eq(mapOf("TYPE" to "ACTIVE_PRISONERS")), isNull())
      verify(telemetryClient).trackEvent(eq("test-reconciliation-report"), check { assertThat(it).containsEntry("TYPE", "ACTIVE_PRISONERS") }, isNull())
    }
  }

  @Nested
  inner class Report {
    @BeforeEach
    fun setUp() = runTest {
      stubAllPrisoners("A0001TZ", "A0002TZ", "A0003TZ")
    }

    @Test
    fun `reports counts when no prisoners mismatch`() = runTest {
      report(ALL_PRISONERS).run()

      verify(telemetryClient).trackEvent(
        eq("test-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "3")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("pages-count", "1")
          assertThat(it).containsEntry("success", "true")
          assertThat(it).containsEntry("offenderNos", "")
        },
        isNull(),
      )
    }

    @Test
    fun `reports the offender numbers of prisoners that mismatch`() = runTest {
      mismatchesFor = { if (it == "A0001TZ" || it == "A0003TZ") listOf("difference") else emptyList() }

      report(ALL_PRISONERS).run()

      verify(telemetryClient).trackEvent(
        eq("test-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("offenderNos", "A0001TZ, A0003TZ")
        },
        isNull(),
      )
    }

    @Test
    fun `lists no more than 10 mismatched offender numbers but counts them all`() = runTest {
      val offenderNos = (1..12).map { "A%04dTZ".format(it) }
      stubAllPrisoners(*offenderNos.toTypedArray())
      mismatchesFor = { listOf("difference") }

      report(ALL_PRISONERS, pageSize = 20).run()

      verify(telemetryClient).trackEvent(
        eq("test-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "12")
          assertThat(it["offenderNos"]!!.split(", ")).isEqualTo(offenderNos.take(10))
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class CustomReportProperties {
    @Test
    fun `mismatch summary is built from the mismatches of each prisoner instead of the offender numbers`() = runTest {
      stubAllPrisoners("A0001TZ", "A0002TZ", "A0003TZ")
      mismatchesFor = { if (it == "A0002TZ") listOf("one", "two") else emptyList() }

      PrisonerReconciliationReport(
        telemetryClient,
        nomisApi,
        names,
        ALL_PRISONERS,
        pageSize = 10,
        threadCount = 2,
        mismatchSummary = { prisoners -> prisoners.associate { it.offenderNo to "count=${it.mismatches.size}" } },
        check = { mismatchesFor(it) },
      ).run()

      verify(telemetryClient).trackEvent(
        eq("test-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("A0002TZ", "count=2")
          assertThat(it).doesNotContainKey("offenderNos")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class CheckError {
    @Test
    fun `a prisoner that cannot be checked is reported and the rest are still checked`() = runTest {
      stubAllPrisoners("A0001TZ", "A0002TZ")
      mismatchesFor = { if (it == "A0001TZ") throw RuntimeException("mapping service down") else emptyList() }

      report(ALL_PRISONERS).run()

      verify(telemetryClient).trackEvent(
        eq("test-reconciliation-mismatch-error"),
        eq(mapOf("offenderNo" to "A0001TZ", "reason" to "mapping service down")),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("test-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "2")
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class PageError {
    @Test
    fun `all prisoners page failure is reported against the offenderId cursor`() = runTest {
      whenever(nomisApi.getAllPrisoners(any(), any())).thenThrow(RuntimeException("NOMIS down"))

      report(ALL_PRISONERS).run()

      verify(telemetryClient).trackEvent(eq("test-reconciliation-mismatch-page-error"), eq(mapOf("offenderId" to "0")), isNull())
    }

    @Test
    fun `active bookings page failure is reported against the booking cursor`() = runTest {
      whenever(nomisApi.getAllLatestBookings(any(), any(), any())).thenThrow(RuntimeException("NOMIS down"))

      report(ACTIVE_PRISONERS).run()

      verify(telemetryClient).trackEvent(eq("test-reconciliation-mismatch-page-error"), eq(mapOf("booking" to "0")), isNull())
    }

    @Test
    fun `a failed page is skipped and the report carries on from the next page`() = runTest {
      whenever(nomisApi.getAllPrisoners(0, 2))
        .thenThrow(RuntimeException("NOMIS down"))
      whenever(nomisApi.getAllPrisoners(2, 2))
        .thenReturn(PrisonerNosWithLast(prisonerIds = listOf(PrisonerId("A0003TZ")), lastOffenderId = 3))

      report(ALL_PRISONERS, pageSize = 2).run()

      verify(telemetryClient).trackEvent(
        eq("test-reconciliation-report"),
        check {
          assertThat(it).containsEntry("prisoners-count", "1")
          assertThat(it).containsEntry("pages-count", "1")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }
  }
}
