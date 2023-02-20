package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityCategory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityLite
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityPay
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.PrisonPayBand
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.OffenderProgramProfileResponse
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
    ActivitiesService(activitiesApiService, nomisApiService, mappingService, updateQueueService, telemetryClient)

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
    fun `should log an activity created event`() {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = NOMIS_COURSE_ACTIVITY_ID)
      )

      activitiesService.createActivity(aDomainEvent())

      verify(telemetryClient).trackEvent(
        eq("activity-created-event"),
        check {
          assertThat(it["courseActivityId"]).isEqualTo("$NOMIS_COURSE_ACTIVITY_ID")
          assertThat(it["activityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
      )
    }

    @Test
    fun `should handle very long activity and schedule descriptions`() {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule().copy(description = "A schedule description that is very very long")
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(
        newActivity().copy(summary = "An activity summary that is very very very long")
      )
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = NOMIS_COURSE_ACTIVITY_ID)
      )

      activitiesService.createActivity(aDomainEvent())

      verify(nomisApiService).createActivity(
        check {
          assertThat(it.description.length).isLessThanOrEqualTo(40)
          assertThat(it.code.length).isLessThanOrEqualTo(20)
        }
      )
    }

    @Test
    fun `should not update NOMIS if activity already mapped (exists in nomis)`() {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleIdOrNull(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "A_TYPE"
        )
      )

      activitiesService.createActivity(aDomainEvent())

      verifyNoInteractions(nomisApiService)
    }

    @Test
    fun `should log a creation failure`() {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenThrow(RuntimeException("test"))

      assertThatThrownBy { activitiesService.createActivity(aDomainEvent()) }
        .isInstanceOf(RuntimeException::class.java)

      verify(telemetryClient).trackEvent(
        eq("activity-create-failed"),
        check {
          assertThat(it["activityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
      )
    }

    @Test
    fun `should log a mapping creation failure`() {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = NOMIS_COURSE_ACTIVITY_ID)
      )
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      activitiesService.createActivity(aDomainEvent())

      verify(telemetryClient).trackEvent(
        eq("activity-create-map-failed"),
        check {
          assertThat(it["courseActivityId"]).isEqualTo("$NOMIS_COURSE_ACTIVITY_ID")
          assertThat(it["activityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
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
    fun `should throw and raise telemetry if cannot load Activity Schedule`() {
      whenever(activitiesApiService.getActivitySchedule(anyLong()))
        .thenThrow(WebClientResponseException.NotFound::class.java)

      assertThatThrownBy {
        activitiesService.updateActivity(aDomainEvent())
      }.isInstanceOf(WebClientResponseException.NotFound::class.java)

      verify(activitiesApiService).getActivitySchedule(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()))
        },
        isNull()
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot load Activity `() {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(activitiesApiService.getActivity(anyLong()))
        .thenThrow(WebClientResponseException.NotFound::class.java)

      assertThatThrownBy {
        activitiesService.updateActivity(aDomainEvent())
      }.isInstanceOf(WebClientResponseException.NotFound::class.java)

      verify(activitiesApiService).getActivity(ACTIVITY_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyEntriesOf(mutableMapOf("activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()))
        },
        isNull()
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot find mappings`() {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong()))
        .thenThrow(WebClientResponseException.NotFound::class.java)

      assertThatThrownBy {
        activitiesService.updateActivity(aDomainEvent())
      }.isInstanceOf(WebClientResponseException.NotFound::class.java)

      verify(mappingService).getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "activityId" to ACTIVITY_ID.toString()
            )
          )
        },
        isNull()
      )
    }

    @Test
    fun `should throw and raise telemetry if fail to update Nomis`() {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID, "ACTIVITY_CREATED", LocalDateTime.now()
        )
      )
      whenever(nomisApiService.updateActivity(anyLong(), any()))
        .thenThrow(WebClientResponseException.ServiceUnavailable::class.java)

      assertThatThrownBy {
        activitiesService.updateActivity(aDomainEvent())
      }.isInstanceOf(WebClientResponseException.ServiceUnavailable::class.java)

      verify(nomisApiService).updateActivity(eq(NOMIS_COURSE_ACTIVITY_ID), any())
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "activityId" to ACTIVITY_ID.toString(),
              "nomisCourseActivityId" to NOMIS_COURSE_ACTIVITY_ID.toString()
            )
          )
        },
        isNull()
      )
    }

    @Test
    fun `should raise telemetry when update of Nomis successful`() {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule(endDate = LocalDate.now().plusDays(1)))
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID, "ACTIVITY_CREATED", LocalDateTime.now()
        )
      )

      activitiesService.updateActivity(aDomainEvent())

      verify(nomisApiService).updateActivity(
        eq(NOMIS_COURSE_ACTIVITY_ID),
        check {
          assertThat(it.endDate).isEqualTo(LocalDate.now().plusDays(1))
          assertThat(it.internalLocationId).isEqualTo(345)
          assertThat(it.payRates).containsExactlyElementsOf(listOf(PayRateRequest("BAS", "1", BigDecimal.valueOf(1.5).setScale(2))))
        }
      )
      verify(telemetryClient).trackEvent(
        eq("activity-amend-event"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "activityId" to ACTIVITY_ID.toString(),
              "nomisCourseActivityId" to NOMIS_COURSE_ACTIVITY_ID.toString()
            )
          )
        },
        isNull()
      )
    }
  }

  @Nested
  inner class Allocate {

    @Test
    fun `should log an allocation event`() {

      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        )
      )
      whenever(nomisApiService.createAllocation(eq(NOMIS_COURSE_ACTIVITY_ID), any())).thenReturn(
        OffenderProgramProfileResponse(offenderProgramReferenceId = OFFENDER_PROGRAM_REFERENCE_ID)
      )

      activitiesService.createAllocation(
        AllocationDomainEvent(
          eventType = "dummy",
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
          additionalInformation = AllocationAdditionalInformation(
            scheduleId = ACTIVITY_SCHEDULE_ID,
            allocationId = ALLOCATION_ID,
          ),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-created-event"),
        check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
          assertThat(it["offenderProgramReferenceId"]).isEqualTo("$OFFENDER_PROGRAM_REFERENCE_ID")
        },
        isNull()
      )
    }

    @Test
    fun `should log a creation failure`() {
      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        )
      )
      whenever(nomisApiService.createAllocation(any(), any())).thenThrow(RuntimeException("test"))

      assertThatThrownBy {
        activitiesService.createAllocation(
          AllocationDomainEvent(
            eventType = "dummy",
            version = "1.0",
            description = "description",
            occurredAt = LocalDateTime.now(),
            additionalInformation = AllocationAdditionalInformation(
              scheduleId = ACTIVITY_SCHEDULE_ID,
              allocationId = ALLOCATION_ID,
            ),
          )
        )
      }.hasMessage("test")

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-create-failed"),
        check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
        },
        isNull()
      )
    }
  }

  @Nested
  inner class Deallocate {

    @Test
    fun `should log an allocation event`() {

      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        )
      )
      whenever(nomisApiService.deallocate(eq(NOMIS_COURSE_ACTIVITY_ID), any(), any())).thenReturn(
        OffenderProgramProfileResponse(offenderProgramReferenceId = OFFENDER_PROGRAM_REFERENCE_ID)
      )

      activitiesService.deallocate(
        AllocationDomainEvent(
          eventType = "dummy",
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
          additionalInformation = AllocationAdditionalInformation(
            scheduleId = ACTIVITY_SCHEDULE_ID,
            allocationId = ALLOCATION_ID,
          ),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("activity-deallocate-event"),
        check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
          assertThat(it["offenderProgramReferenceId"]).isEqualTo("$OFFENDER_PROGRAM_REFERENCE_ID")
        },
        isNull()
      )
    }

    @Test
    fun `should log a nomis failure`() {
      whenever(activitiesApiService.getAllocation(ALLOCATION_ID)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED",
        )
      )
      whenever(nomisApiService.deallocate(any(), any(), any())).thenThrow(RuntimeException("test"))

      assertThatThrownBy {
        activitiesService.deallocate(
          AllocationDomainEvent(
            eventType = "dummy",
            version = "1.0",
            description = "description",
            occurredAt = LocalDateTime.now(),
            additionalInformation = AllocationAdditionalInformation(
              scheduleId = ACTIVITY_SCHEDULE_ID,
              allocationId = ALLOCATION_ID,
            ),
          )
        )
      }.hasMessage("test")

      verify(telemetryClient).trackEvent(
        eq("activity-deallocate-failed"),
        check {
          assertThat(it["allocationId"]).isEqualTo("$ALLOCATION_ID")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["bookingId"]).isEqualTo("$BOOKING_ID")
        },
        isNull()
      )
    }
  }

  @Nested
  inner class Retry {

    @Test
    fun `should call mapping service`() {
      activitiesService.createRetry(
        ActivityContext(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID
        )
      )

      verify(mappingService).createMapping(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_COURSE_ACTIVITY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "ACTIVITY_CREATED"
        )
      )
    }
  }
}

private fun newActivitySchedule(endDate: LocalDate? = null): ActivitySchedule = ActivitySchedule(
  id = ACTIVITY_SCHEDULE_ID,
  instances = emptyList(),
  allocations = emptyList(),
  description = "description",
  suspensions = emptyList(),
  capacity = 35,
  internalLocation = InternalLocation(
    id = 345,
    code = "A-ROOM",
    description = "Room description"
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
  ),
  slots = emptyList(),
  startDate = LocalDate.now(),
  endDate = endDate,
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
      rate = 150
    )
  ),
  startDate = LocalDate.now(),
  createdTime = LocalDateTime.now(),
  createdBy = "me",
  minimumIncentiveLevel = "Basic",
  minimumIncentiveNomisCode = "BAS",
  riskLevel = "high",
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
    scheduleId = 123,
  )
}
