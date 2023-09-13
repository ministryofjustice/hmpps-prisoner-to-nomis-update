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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

class AllocationServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: NomisApiService = mock()
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
        UpsertAllocationResponse(offenderProgramReferenceId = OFFENDER_PROGRAM_REFERENCE_ID, created = true),
      )

      allocationService.upsertAllocationEvent(
        AllocationDomainEvent(
          eventType = "dummy",
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
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
              occurredAt = LocalDateTime.now(),
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
  )
}
