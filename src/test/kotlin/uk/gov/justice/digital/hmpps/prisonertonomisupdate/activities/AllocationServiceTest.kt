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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.PrisonPayBand
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

private const val ACTIVITY_SCHEDULE_ID: Long = 100
private const val NOMIS_COURSE_ACTIVITY_ID: Long = 300
private const val ALLOCATION_ID: Long = 400
private const val OFFENDER_PROGRAM_REFERENCE_ID: Long = 500
private const val BOOKING_ID: Long = 600
private const val OFFENDER_NO = "A1234AA"

class AllocationServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val allocationService = AllocationService(activitiesApiService, nomisApiService, mappingService, telemetryClient)

  @Nested
  inner class Allocate {

    @Test
    fun `should log an allocation event`() = runTest {
      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        ),
      )
      whenever(nomisApiService.createAllocation(eq(NOMIS_COURSE_ACTIVITY_ID), any())).thenReturn(
        CreateAllocationResponse(offenderProgramReferenceId = OFFENDER_PROGRAM_REFERENCE_ID),
      )

      allocationService.createAllocation(
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
        eq("activity-allocation-create-success"),
        org.mockito.kotlin.check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
          assertThat(it["offenderProgramReferenceId"]).isEqualTo("$OFFENDER_PROGRAM_REFERENCE_ID")
        },
        isNull(),
      )
    }

    @Test
    fun `should log a creation failure`() = runTest {
      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        ),
      )
      whenever(nomisApiService.createAllocation(any(), any())).thenThrow(RuntimeException("test"))

      assertThat(
        assertThrows<RuntimeException> {
          allocationService.createAllocation(
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
        eq("activity-allocation-create-failed"),
        org.mockito.kotlin.check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class Deallocate {

    @Test
    fun `should log an allocation event`() = runTest {
      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        ),
      )
      whenever(nomisApiService.deallocate(eq(NOMIS_COURSE_ACTIVITY_ID), any())).thenReturn(
        CreateAllocationResponse(offenderProgramReferenceId = OFFENDER_PROGRAM_REFERENCE_ID),
      )

      allocationService.deallocate(
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
        eq("activity-deallocate-success"),
        org.mockito.kotlin.check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
          assertThat(it["offenderProgramReferenceId"]).isEqualTo("$OFFENDER_PROGRAM_REFERENCE_ID")
        },
        isNull(),
      )
    }

    @Test
    fun `should log a nomis failure`() = runTest {
      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        ),
      )
      whenever(nomisApiService.deallocate(any(), any())).thenThrow(RuntimeException("test"))

      assertThat(
        assertThrows<RuntimeException> {
          allocationService.deallocate(
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
        eq("activity-deallocate-failed"),
        org.mockito.kotlin.check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
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
    bookingId = BOOKING_ID,
    startDate = LocalDate.parse("2023-01-12"),
    endDate = LocalDate.parse("2023-01-13"),
    prisonPayBand = PrisonPayBand(1, 1, "", "", 1, "MDI"),
    scheduleDescription = "description",
    scheduleId = ACTIVITY_SCHEDULE_ID,
    isUnemployment = false,
  )
}
