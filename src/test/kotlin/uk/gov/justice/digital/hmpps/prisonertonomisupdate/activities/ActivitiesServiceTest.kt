@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityCategory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityLite
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityMinimumEducationLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityPay
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.PrisonPayBand
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PayRateRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

private const val ACTIVITY_SCHEDULE_ID: Long = 100
private const val ACTIVITY_ID: Long = 200
private const val NOMIS_COURSE_ACTIVITY_ID: Long = 300
private const val ALLOCATION_ID: Long = 400
private const val OFFENDER_PROGRAM_REFERENCE_ID: Long = 500
private const val BOOKING_ID: Long = 600
private const val OFFENDER_NO = "A1234AA"

internal class ActivitiesServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val updateQueueService: ActivitiesUpdateQueueService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val activitiesService =
    ActivitiesService(
      activitiesApiService,
      nomisApiService,
      mappingService,
      updateQueueService,
      telemetryClient,
      objectMapper(),
    )

  @Nested
  inner class CreateActivity {

    private fun aDomainEvent() =
      ScheduleDomainEvent(
        eventType = "dummy",
        additionalInformation = ScheduleAdditionalInformation(ACTIVITY_SCHEDULE_ID),
        version = "1.0",
        description = "description",
        occurredAt = LocalDateTime.now(),
      )

    @Test
    fun `should log an activity created event`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule(),
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = NOMIS_COURSE_ACTIVITY_ID),
      )

      activitiesService.createActivity(aDomainEvent())

      verify(telemetryClient).trackEvent(
        eq("activity-create-success"),
        check {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("$NOMIS_COURSE_ACTIVITY_ID")
          assertThat(it["activityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull(),
      )
    }

    @Test
    fun `should handle very long activity and schedule descriptions`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule().copy(description = "A schedule description that is very very long"),
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(
        newActivity().copy(summary = "An activity summary that is very very very long"),
      )
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = NOMIS_COURSE_ACTIVITY_ID),
      )

      activitiesService.createActivity(aDomainEvent())

      verify(nomisApiService).createActivity(
        check {
          assertThat(it.description.length).isLessThanOrEqualTo(40)
          assertThat(it.code.length).isLessThanOrEqualTo(12)
        },
      )
    }

    @Test
    fun `should not update NOMIS if activity already mapped (exists in nomis)`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule(),
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleIdOrNull(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "A_TYPE",
        ),
      )

      activitiesService.createActivity(aDomainEvent())

      verifyNoInteractions(nomisApiService)
    }

    @Test
    fun `should log a mapping creation failure`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule(),
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = NOMIS_COURSE_ACTIVITY_ID),
      )
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      activitiesService.createActivity(aDomainEvent())

      verify(telemetryClient).trackEvent(
        eq("activity-mapping-create-failed"),
        check {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("$NOMIS_COURSE_ACTIVITY_ID")
          assertThat(it["activityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class UpdateActivitySchedule {

    private fun aDomainEvent() =
      ScheduleDomainEvent(
        eventType = "activities.activity-schedule.amended",
        additionalInformation = ScheduleAdditionalInformation(ACTIVITY_SCHEDULE_ID),
        version = "1.0",
        description = "description",
        occurredAt = LocalDateTime.now(),
      )

    @Test
    fun `should throw and raise telemetry if cannot load Activity Schedule`() = runTest {
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          LocalDateTime.now(),
        ),
      )
      whenever(activitiesApiService.getActivitySchedule(anyLong()))
        .thenThrow(NotFound::class.java)

      assertThrows<NotFound> {
        activitiesService.updateActivity(aDomainEvent())
      }

      verify(activitiesApiService).getActivitySchedule(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()))
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot load Activity `() = runTest {
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          LocalDateTime.now(),
        ),
      )
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(activitiesApiService.getActivity(anyLong()))
        .thenThrow(NotFound::class.java)

      assertThrows<NotFound> {
        activitiesService.updateActivity(aDomainEvent())
      }

      verify(activitiesApiService).getActivity(ACTIVITY_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("$NOMIS_COURSE_ACTIVITY_ID")
          assertThat(it["activityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot find mappings`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong()))
        .thenThrow(NotFound::class.java)

      assertThrows<NotFound> {
        activitiesService.updateActivity(aDomainEvent())
      }

      verify(mappingService).getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fail to update Nomis`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          LocalDateTime.now(),
        ),
      )
      whenever(nomisApiService.updateActivity(anyLong(), any()))
        .thenThrow(ServiceUnavailable::class.java)

      assertThrows<ServiceUnavailable> {
        activitiesService.updateActivity(aDomainEvent())
      }

      verify(nomisApiService).updateActivity(eq(NOMIS_COURSE_ACTIVITY_ID), any())
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "activityId" to ACTIVITY_ID.toString(),
              "nomisCourseActivityId" to NOMIS_COURSE_ACTIVITY_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should raise telemetry when update of Nomis successful`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule(endDate = LocalDate.now().plusDays(1)))
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          LocalDateTime.now(),
        ),
      )

      activitiesService.updateActivity(aDomainEvent())

      verify(nomisApiService).updateActivity(
        eq(NOMIS_COURSE_ACTIVITY_ID),
        check {
          assertThat(it.endDate).isEqualTo(LocalDate.now().plusDays(1))
          assertThat(it.internalLocationId).isEqualTo(345)
          assertThat(it.payRates).containsExactlyElementsOf(listOf(PayRateRequest("BAS", "1", BigDecimal.valueOf(1.5).setScale(2))))
        },
      )
      verify(telemetryClient).trackEvent(
        eq("activity-amend-success"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "activityId" to ACTIVITY_ID.toString(),
              "nomisCourseActivityId" to NOMIS_COURSE_ACTIVITY_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class AmendScheduleInstances {

    private fun aDomainEvent() =
      ScheduleDomainEvent(
        eventType = "activities.scheduled-instances.amended",
        additionalInformation = ScheduleAdditionalInformation(ACTIVITY_SCHEDULE_ID),
        version = "1.0",
        description = "description",
        occurredAt = LocalDateTime.now(),
      )

    @Test
    fun `should throw and raise telemetry if cannot load Activity Schedule`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(anyLong()))
        .thenThrow(NotFound::class.java)

      assertThrows<NotFound> {
        activitiesService.updateScheduleInstances(aDomainEvent())
      }

      verify(activitiesApiService).getActivitySchedule(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("schedule-instances-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()))
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot find mappings`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong()))
        .thenThrow(NotFound::class.java)

      assertThrows<NotFound> {
        activitiesService.updateScheduleInstances(aDomainEvent())
      }

      verify(mappingService).getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("schedule-instances-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf("activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to update Nomis`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          LocalDateTime.now(),
        ),
      )
      whenever(nomisApiService.updateScheduleInstances(anyLong(), anyList()))
        .thenThrow(ServiceUnavailable::class.java)

      assertThrows<ServiceUnavailable> {
        activitiesService.updateScheduleInstances(aDomainEvent())
      }

      verify(nomisApiService).updateScheduleInstances(eq(NOMIS_COURSE_ACTIVITY_ID), any())
      verify(telemetryClient).trackEvent(
        eq("schedule-instances-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "nomisCourseActivityId" to NOMIS_COURSE_ACTIVITY_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should raise telemetry when update of Nomis successful`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule(endDate = LocalDate.now().plusDays(1)))
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          LocalDateTime.now(),
        ),
      )

      activitiesService.updateScheduleInstances(aDomainEvent())

      verify(nomisApiService).updateScheduleInstances(
        eq(NOMIS_COURSE_ACTIVITY_ID),
        check {
          with(it[0]) {
            assertThat(date).isEqualTo("2023-02-10")
            assertThat(startTime).isEqualTo("08:00")
            assertThat(endTime).isEqualTo("11:00")
          }
          with(it[1]) {
            assertThat(date).isEqualTo("2023-02-11")
            assertThat(startTime).isEqualTo("08:00")
            assertThat(endTime).isEqualTo("11:00")
          }
        },
      )
      verify(telemetryClient).trackEvent(
        eq("schedule-instances-amend-success"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "nomisCourseActivityId" to NOMIS_COURSE_ACTIVITY_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }
  }

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

      activitiesService.createAllocation(
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
        check {
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
          activitiesService.createAllocation(
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
        check {
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

      activitiesService.deallocate(
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
        check {
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
          activitiesService.deallocate(
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
        check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class Retry {

    @Test
    fun `should call mapping service`() = runTest {
      activitiesService.createRetry(
        CreateMappingRetryMessage(
          mapping = ActivityContext(
            nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
            activityScheduleId = ACTIVITY_SCHEDULE_ID,
          ),
          telemetryAttributes = mapOf(),
        ),
      )

      verify(mappingService).createMapping(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        ),
      )
    }
  }
}

private fun newActivitySchedule(endDate: LocalDate? = null): ActivitySchedule = ActivitySchedule(
  id = ACTIVITY_SCHEDULE_ID,
  instances = listOf(
    ScheduledInstance(
      id = 1,
      date = LocalDate.parse("2023-02-10"),
      startTime = "08:00",
      endTime = "11:00",
      cancelled = false,
      attendances = listOf(),
    ),
    ScheduledInstance(
      id = 2,
      date = LocalDate.parse("2023-02-11"),
      startTime = "08:00",
      endTime = "11:00",
      cancelled = false,
      attendances = listOf(),
    ),
  ),
  allocations = emptyList(),
  description = "description",
  suspensions = emptyList(),
  capacity = 35,
  internalLocation = InternalLocation(
    id = 345,
    code = "A-ROOM",
    description = "Room description",
  ),
  activity = ActivityLite(
    id = ACTIVITY_ID,
    prisonCode = "MDI",
    attendanceRequired = false,
    inCell = false,
    pieceWork = false,
    outsideWork = false,
    summary = "summary",
    category = ActivityCategory(
      id = 67,
      code = "CAT",
      name = "Category",
      description = "description",
    ),
    riskLevel = "high",
    payPerSession = ActivityLite.PayPerSession.h,
    minimumIncentiveLevel = "Basic",
    minimumIncentiveNomisCode = "BAS",
    minimumEducationLevel = listOf(
      ActivityMinimumEducationLevel(
        id = 123456,
        educationLevelCode = "Basic",
        educationLevelDescription = "Basic",
      ),
    ),
  ),
  slots = emptyList(),
  startDate = LocalDate.now(),
  endDate = endDate,
  runsOnBankHoliday = true,
)

private fun newActivity(): Activity = Activity(
  id = ACTIVITY_ID,
  prisonCode = "MDI",
  attendanceRequired = false,
  inCell = false,
  pieceWork = false,
  outsideWork = false,
  summary = "summary",
  category = ActivityCategory(
    id = 67,
    code = "CAT",
    name = "Category",
    description = "description",
  ),
  payPerSession = Activity.PayPerSession.h,
  eligibilityRules = emptyList(),
  schedules = emptyList(),
  waitingList = emptyList(),
  pay = listOf(
    ActivityPay(
      id = 1,
      prisonPayBand = PrisonPayBand(2, 1, "", "", 1, "MDI"),
      incentiveNomisCode = "BAS",
      incentiveLevel = "Basic",
      rate = 150,
    ),
  ),
  startDate = LocalDate.now(),
  createdTime = LocalDateTime.now(),
  createdBy = "me",
  minimumIncentiveLevel = "Basic",
  minimumIncentiveNomisCode = "BAS",
  riskLevel = "high",
  minimumEducationLevel = listOf(
    ActivityMinimumEducationLevel(
      id = 123456,
      educationLevelCode = "Basic",
      educationLevelDescription = "Basic",
    ),
  ),
)

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
