package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitTimeSlotForPrisonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitTimeSlotResponse.DayOfWeek
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncTimeSlotSummaryItem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncVisitSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.VisitSlotsNomisApiMockServer.Companion.visitSlotResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.VisitSlotsNomisApiMockServer.Companion.visitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncTimeSlotSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncTimeSlotSummaryItem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@SpringAPIServiceTest
@Import(
  VisitSlotsReconciliationService::class,
  VisitSlotsNomisApiService::class,
  OfficialVisitsDpsApiService::class,
  OfficialVisitsConfiguration::class,
  RetryApiService::class,
  VisitSlotsNomisApiMockServer::class,
)
class VisitSlotsReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var nomisApi: VisitSlotsNomisApiMockServer

  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @Autowired
  private lateinit var service: VisitSlotsReconciliationService

  @Nested
  inner class CheckPrisonForMismatches {
    @Nested
    inner class WhenAllMatches {
      @BeforeEach
      fun setUp() {
        stubSlots(
          prisonId = "BXI",
          nomisTimeSlots = listOf(visitTimeSlotResponse()),
          dpsTimeSlots = listOf(syncTimeSlotSummaryItem()),
        )
      }

      @Test
      fun `will return no mismatch`() = runTest {
        assertThat(service.checkPrisonForMismatches("BXI")).isEmpty()
      }
    }

    @Nested
    inner class WhenNumberSlotsPerDayMismatch {
      @BeforeEach
      fun setUp() {
        stubSlots(
          prisonId = "BXI",
          nomisTimeSlots = listOf(visitTimeSlotResponse().copy(dayOfWeek = DayOfWeek.MON), visitTimeSlotResponse().copy(dayOfWeek = DayOfWeek.TUE)),
          dpsTimeSlots = listOf(syncTimeSlotSummaryItem().copy(timeSlot = syncTimeSlot().copy(dayCode = DayType.MON))),
        )
      }

      @Test
      fun `will return mismatch`() = runTest {
        assertThat(service.checkPrisonForMismatches("BXI")).containsExactlyInAnyOrder(
          MismatchTimeSlot(
            prisonId = "BXI",
            dayOfWeek = "TUE",
            reason = "time-slot-count-mismatch",
          ),
        )
        verify(telemetryClient).trackEvent(
          eq("visit-slots-reconciliation-mismatch"),
          check {
            assertThat(it["prisonId"]).isEqualTo("BXI")
            assertThat(it["dayOfWeek"]).isEqualTo("TUE")
            assertThat(it["nomisCount"]).isEqualTo("1")
            assertThat(it["dpsCount"]).isEqualTo("0")
            assertThat(it["reason"]).isEqualTo("time-slot-count-mismatch")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenTimeSlotDiffers {
      @BeforeEach
      fun setUp() {
        stubSlots(
          prisonId = "BXI",
          nomisTimeSlots = listOf(visitTimeSlotResponse().copy(expiryDate = LocalDate.now().minusDays(1))),
          dpsTimeSlots = listOf(syncTimeSlotSummaryItem().copy(timeSlot = syncTimeSlot().copy(expiryDate = LocalDate.now().plusDays(1)))),
        )
      }

      @Test
      fun `will return mismatch`() = runTest {
        assertThat(service.checkPrisonForMismatches("BXI")).containsExactlyInAnyOrder(
          MismatchTimeSlot(
            prisonId = "BXI",
            dayOfWeek = "MON",
            reason = "matching-time-slot-not-found",
          ),
        )
        verify(telemetryClient).trackEvent(
          eq("visit-slots-reconciliation-mismatch"),
          check {
            assertThat(it["prisonId"]).isEqualTo("BXI")
            assertThat(it["dayOfWeek"]).isEqualTo("MON")
            assertThat(it["startTime"]).isEqualTo("10:00")
            assertThat(it["endTime"]).isEqualTo("11:00")
            assertThat(it["nomisTimeSlotSequence"]).isEqualTo("1")
            assertThat(it["reason"]).isEqualTo("matching-time-slot-not-found")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenVisitSlotDiffers {
      @BeforeEach
      fun setUp() {
        stubSlots(
          prisonId = "BXI",
          nomisTimeSlots = listOf(visitTimeSlotResponse().copy(visitSlots = listOf(visitSlotResponse().copy(maxAdults = 9)))),
          dpsTimeSlots = listOf(syncTimeSlotSummaryItem().copy(visitSlots = listOf(syncVisitSlot().copy(maxAdults = 1)))),
        )
      }

      @Test
      fun `will return mismatch`() = runTest {
        assertThat(service.checkPrisonForMismatches("BXI")).containsExactlyInAnyOrder(
          MismatchTimeSlot(
            prisonId = "BXI",
            dayOfWeek = "MON",
            reason = "matching-time-slot-not-found",
          ),
        )
        verify(telemetryClient).trackEvent(
          eq("visit-slots-reconciliation-mismatch"),
          check {
            assertThat(it["prisonId"]).isEqualTo("BXI")
            assertThat(it["dayOfWeek"]).isEqualTo("MON")
            assertThat(it["startTime"]).isEqualTo("10:00")
            assertThat(it["endTime"]).isEqualTo("11:00")
            assertThat(it["nomisTimeSlotSequence"]).isEqualTo("1")
            assertThat(it["reason"]).isEqualTo("matching-time-slot-not-found")
          },
          isNull(),
        )
      }
    }
  }
  fun stubSlots(prisonId: String = "BXI", nomisTimeSlots: List<VisitTimeSlotResponse>, dpsTimeSlots: List<SyncTimeSlotSummaryItem>) {
    nomisApi.stubGetTimeSlotsForPrison(
      prisonId,
      response = VisitTimeSlotForPrisonResponse(
        prisonId = prisonId,
        timeSlots = nomisTimeSlots,
      ),
    )
    dpsApi.stubGetTimeSlotsForPrison(
      prisonId,
      response = SyncTimeSlotSummary(
        prisonCode = prisonId,
        timeSlots = dpsTimeSlots,
      ),
    )
  }
}
