@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation.Status.ACTIVE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.PrisonPayBand
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Slot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AllocationExclusion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationResponse
import java.time.LocalDate

class AllocationServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: ActivitiesNomisApiService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val allocationService = AllocationService(activitiesApiService, nomisApiService, mappingService, telemetryClient)

  @Nested
  inner class UpsertAllocation {

    @Test
    fun `should publish telemetry for an allocation event`() = runTest {
      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappings(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_CRS_ACTY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          activityId = ACTIVITY_ID,
          mappingType = "ACTIVITY_CREATED",
        ),
      )
      whenever(nomisApiService.upsertAllocation(eq(NOMIS_CRS_ACTY_ID), any())).thenReturn(
        UpsertAllocationResponse(offenderProgramReferenceId = OFFENDER_PROGRAM_REFERENCE_ID, created = true, prisonId = "MDI"),
      )

      allocationService.upsertAllocationEvent(
        AllocationDomainEvent(
          eventType = "dummy",
          version = "1.0",
          description = "description",
          additionalInformation = AllocationAdditionalInformation(
            allocationId = ALLOCATION_ID,
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-success"),
        org.mockito.kotlin.check {
          assertThat(it["dpsAllocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$NOMIS_BOOKING_ID")
          assertThat(it["nomisAllocationId"]).isEqualTo("$OFFENDER_PROGRAM_REFERENCE_ID")
          assertThat(it["prisonId"]).isEqualTo("MDI")
        },
        isNull(),
      )
    }

    @Test
    fun `should publish telemetry for a failed allocation event`() = runTest {
      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappings(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_CRS_ACTY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          activityId = ACTIVITY_ID,
          mappingType = "ACTIVITY_CREATED",
        ),
      )
      whenever(nomisApiService.upsertAllocation(any(), any())).thenThrow(RuntimeException("test"))

      assertThat(
        assertThrows<RuntimeException> {
          allocationService.upsertAllocationEvent(
            AllocationDomainEvent(
              eventType = "dummy",
              version = "1.0",
              description = "description",
              additionalInformation = AllocationAdditionalInformation(
                allocationId = ALLOCATION_ID,
              ),
            ),
          )
        }.message,
      ).isEqualTo("test")

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-failed"),
        org.mockito.kotlin.check {
          assertThat(it["dpsAllocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$NOMIS_BOOKING_ID")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class ToAllocationExclusionRequest {
    @Test
    fun `should return empty list`() {
      assertThat(allocationService.toAllocationExclusions(emptyList())).isEmpty()
    }

    @Test
    fun `should return single slot`() {
      val exclusions = listOf(aSlot(timeSlot = "AM", daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY)))

      assertThat(allocationService.toAllocationExclusions(exclusions))
        .containsExactly(AllocationExclusion(AllocationExclusion.Day.MON, AllocationExclusion.Slot.AM))
    }

    @Test
    fun `should return multiple slots`() {
      val exclusions = listOf(
        aSlot(timeSlot = "AM", daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY)),
        aSlot(timeSlot = "PM", daysOfWeek = setOf(Slot.DaysOfWeek.TUESDAY, Slot.DaysOfWeek.WEDNESDAY)),
      )

      assertThat(allocationService.toAllocationExclusions(exclusions))
        .containsExactlyInAnyOrder(
          AllocationExclusion(AllocationExclusion.Day.MON, AllocationExclusion.Slot.AM),
          AllocationExclusion(AllocationExclusion.Day.TUE, AllocationExclusion.Slot.PM),
          AllocationExclusion(AllocationExclusion.Day.WED, AllocationExclusion.Slot.PM),
        )
    }

    @Test
    fun `should return a whole day`() {
      val exclusions = listOf(
        aSlot(timeSlot = "AM", daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY)),
        aSlot(timeSlot = "PM", daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY)),
        aSlot(timeSlot = "ED", daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY)),
      )

      assertThat(allocationService.toAllocationExclusions(exclusions))
        .containsExactlyInAnyOrder(
          AllocationExclusion(AllocationExclusion.Day.MON, null),
        )
    }

    @Test
    fun `should handle multiple scenarios`() {
      val exclusions = listOf(
        aSlot(timeSlot = "AM", daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY, Slot.DaysOfWeek.WEDNESDAY, Slot.DaysOfWeek.THURSDAY)),
        aSlot(timeSlot = "PM", daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY, Slot.DaysOfWeek.THURSDAY, Slot.DaysOfWeek.SATURDAY)),
        aSlot(timeSlot = "ED", daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY, Slot.DaysOfWeek.THURSDAY, Slot.DaysOfWeek.SUNDAY)),
      )

      assertThat(allocationService.toAllocationExclusions(exclusions))
        .containsExactlyInAnyOrder(
          AllocationExclusion(AllocationExclusion.Day.MON, null),
          AllocationExclusion(AllocationExclusion.Day.WED, AllocationExclusion.Slot.AM),
          AllocationExclusion(AllocationExclusion.Day.THU, null),
          AllocationExclusion(AllocationExclusion.Day.SAT, AllocationExclusion.Slot.PM),
          AllocationExclusion(AllocationExclusion.Day.SUN, AllocationExclusion.Slot.ED),
        )
    }

    private fun aSlot(timeSlot: String, daysOfWeek: Set<Slot.DaysOfWeek>) =
      Slot(
        weekNumber = 1,
        timeSlot = timeSlot,
        // we don't use these fields so don't need to set them up
        monday = false,
        tuesday = false,
        wednesday = false,
        thursday = true,
        friday = false,
        saturday = false,
        sunday = true,
        daysOfWeek = daysOfWeek,
      )
  }
}

private fun newAllocation(): Allocation {
  return Allocation(
    id = ALLOCATION_ID,
    prisonerNumber = OFFENDER_NO,
    activitySummary = "summary",
    activityId = ACTIVITY_ID,
    bookingId = NOMIS_BOOKING_ID,
    startDate = LocalDate.parse("2023-01-12"),
    endDate = LocalDate.parse("2023-01-13"),
    prisonPayBand = PrisonPayBand(1, 1, "", "", 1, "MDI"),
    scheduleDescription = "description",
    scheduleId = ACTIVITY_SCHEDULE_ID,
    isUnemployment = false,
    status = ACTIVE,
    exclusions = emptyList(),
  )
}
