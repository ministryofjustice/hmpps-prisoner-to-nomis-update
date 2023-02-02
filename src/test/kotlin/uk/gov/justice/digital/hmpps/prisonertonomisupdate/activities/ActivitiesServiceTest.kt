package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Activity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityCategory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityLite
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.Allocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.OffenderProgramProfileResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

private const val activityScheduleId: Long = 100
private const val activityId: Long = 200
private const val nomisCourseActivityId: Long = 300
private const val allocationId: Long = 400
private const val offenderProgramReferenceId: Long = 500
private const val bookingId: Long = 600
private const val offenderNo = "A1234AA"

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

    @Test
    fun `should log an activity created event`() {
      whenever(activitiesApiService.getActivitySchedule(activityScheduleId)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(activityId)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = nomisCourseActivityId)
      )

      activitiesService.createActivity(
        OutboundHMPPSDomainEvent(
          eventType = "dummy",
          identifier = activityScheduleId,
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("activity-created-event"),
        check {
          assertThat(it["courseActivityId"]).isEqualTo("$nomisCourseActivityId")
          assertThat(it["activityScheduleId"]).isEqualTo("$activityScheduleId")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
      )
    }

    @Test
    fun `should not update NOMIS if activity already mapped (exists in nomis)`() {
      whenever(activitiesApiService.getActivitySchedule(activityScheduleId)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(activityId)).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleIdOrNull(activityScheduleId)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = nomisCourseActivityId,
          activityScheduleId = activityScheduleId,
          mappingType = "A_TYPE"
        )
      )

      activitiesService.createActivity(
        OutboundHMPPSDomainEvent(
          eventType = "dummy",
          identifier = activityScheduleId,
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
        )
      )

      verifyNoInteractions(nomisApiService)
    }

    @Test
    fun `should log a creation failure`() {
      whenever(activitiesApiService.getActivitySchedule(activityScheduleId)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(activityId)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenThrow(RuntimeException("test"))

      assertThatThrownBy {
        activitiesService.createActivity(
          OutboundHMPPSDomainEvent(
            eventType = "dummy",
            identifier = activityScheduleId,
            version = "1.0",
            description = "description",
            occurredAt = LocalDateTime.now(),
          )
        )
      }.isInstanceOf(RuntimeException::class.java)

      verify(telemetryClient).trackEvent(
        eq("activity-create-failed"),
        check {
          assertThat(it["activityScheduleId"]).isEqualTo("$activityScheduleId")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
      )
    }

    @Test
    fun `should log a mapping creation failure`() {
      whenever(activitiesApiService.getActivitySchedule(activityScheduleId)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(activityId)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = nomisCourseActivityId)
      )
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      activitiesService.createActivity(
        OutboundHMPPSDomainEvent(
          eventType = "dummy",
          identifier = activityScheduleId,
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("activity-create-map-failed"),
        check {
          assertThat(it["courseActivityId"]).isEqualTo("$nomisCourseActivityId")
          assertThat(it["activityScheduleId"]).isEqualTo("$activityScheduleId")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
      )
    }
  }

  @Nested
  inner class Allocate {

    @Test
    fun `should log an allocation event`() {

      whenever(activitiesApiService.getAllocation(allocationId)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(activityScheduleId)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = nomisCourseActivityId,
          activityScheduleId = activityScheduleId,
          mappingType = "ACTIVITY_CREATED",
        )
      )
      whenever(nomisApiService.createAllocation(eq(nomisCourseActivityId), any())).thenReturn(
        OffenderProgramProfileResponse(offenderProgramReferenceId = offenderProgramReferenceId)
      )

      activitiesService.createAllocation(
        AllocationDomainEvent(
          eventType = "dummy",
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
          additionalInformation = AllocationAdditionalInformation(
            scheduleId = activityScheduleId,
            allocationId = allocationId,
          ),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-created-event"),
        check {
          assertThat(it["allocationId"]).isEqualTo("$allocationId")
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["bookingId"]).isEqualTo("$bookingId")
          assertThat(it["offenderProgramReferenceId"]).isEqualTo("$offenderProgramReferenceId")
        },
        isNull()
      )
    }

    @Test
    fun `should log a creation failure`() {
      whenever(activitiesApiService.getAllocation(allocationId)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(activityScheduleId)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = nomisCourseActivityId,
          activityScheduleId = activityScheduleId,
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
              scheduleId = activityScheduleId,
              allocationId = allocationId,
            ),
          )
        )
      }.hasMessage("test")

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-create-failed"),
        check {
          assertThat(it["allocationId"]).isEqualTo("$allocationId")
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["bookingId"]).isEqualTo("$bookingId")
        },
        isNull()
      )
    }
  }

  @Nested
  inner class Deallocate {

    @Test
    fun `should log an allocation event`() {

      whenever(activitiesApiService.getAllocation(allocationId)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(activityScheduleId)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = nomisCourseActivityId,
          activityScheduleId = activityScheduleId,
          mappingType = "ACTIVITY_CREATED",
        )
      )
      whenever(nomisApiService.deallocate(eq(nomisCourseActivityId), any(), any())).thenReturn(
        OffenderProgramProfileResponse(offenderProgramReferenceId = offenderProgramReferenceId)
      )

      activitiesService.deallocate(
        AllocationDomainEvent(
          eventType = "dummy",
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
          additionalInformation = AllocationAdditionalInformation(
            scheduleId = activityScheduleId,
            allocationId = allocationId,
          ),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("activity-deallocate-event"),
        check {
          assertThat(it["allocationId"]).isEqualTo("$allocationId")
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["bookingId"]).isEqualTo("$bookingId")
          assertThat(it["offenderProgramReferenceId"]).isEqualTo("$offenderProgramReferenceId")
        },
        isNull()
      )
    }

    @Test
    fun `should log a nomis failure`() {
      whenever(activitiesApiService.getAllocation(allocationId)).thenReturn(newAllocation())
      whenever(mappingService.getMappingGivenActivityScheduleId(activityScheduleId)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = nomisCourseActivityId,
          activityScheduleId = activityScheduleId,
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
              scheduleId = activityScheduleId,
              allocationId = allocationId,
            ),
          )
        )
      }.hasMessage("test")

      verify(telemetryClient).trackEvent(
        eq("activity-deallocate-failed"),
        check {
          assertThat(it["allocationId"]).isEqualTo("$allocationId")
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["bookingId"]).isEqualTo("$bookingId")
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
          nomisCourseActivityId = nomisCourseActivityId,
          activityScheduleId = activityScheduleId
        )
      )

      verify(mappingService).createMapping(
        ActivityMappingDto(
          nomisCourseActivityId = nomisCourseActivityId,
          activityScheduleId = activityScheduleId,
          mappingType = "ACTIVITY_CREATED"
        )
      )
    }
  }
}

private fun newActivitySchedule(): ActivitySchedule = ActivitySchedule(
  id = activityScheduleId,
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
    id = activityId,
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
    payPerSession = ActivityLite.PayPerSession.h,
  ),
  slots = emptyList(),
  startDate = LocalDate.now(),
)

private fun newActivity(): Activity = Activity(
  id = activityId,
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
  pay = emptyList(),
  startDate = LocalDate.now(),
  createdTime = OffsetDateTime.now(),
  createdBy = "me",
)

private fun newAllocation(): Allocation {
  return Allocation(
    id = allocationId,
    prisonerNumber = offenderNo,
    activitySummary = "summary",
    bookingId = bookingId,
    startDate = LocalDate.parse("2023-01-12"),
    endDate = LocalDate.parse("2023-01-13"),
    payBand = "PAY1",
    scheduleDescription = "description",
  )
}
