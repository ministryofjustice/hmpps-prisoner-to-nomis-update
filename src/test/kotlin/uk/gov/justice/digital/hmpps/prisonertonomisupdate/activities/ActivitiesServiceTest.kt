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

    private fun aDomainEvent() =
      ScheduleDomainEvent(
        eventType = "dummy",
        additionalInformation = ScheduleAdditionalInformation(activityScheduleId),
        version = "1.0",
        description = "description",
        occurredAt = LocalDateTime.now(),
      )

    @Test
    fun `should log an activity created event`() {
      whenever(activitiesApiService.getActivitySchedule(activityScheduleId)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(activityId)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = nomisCourseActivityId)
      )

      activitiesService.createActivity(aDomainEvent())

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

      activitiesService.createActivity(aDomainEvent())

      verifyNoInteractions(nomisApiService)
    }

    @Test
    fun `should log a creation failure`() {
      whenever(activitiesApiService.getActivitySchedule(activityScheduleId)).thenReturn(
        newActivitySchedule()
      )
      whenever(activitiesApiService.getActivity(activityId)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenThrow(RuntimeException("test"))

      assertThatThrownBy { activitiesService.createActivity(aDomainEvent()) }
        .isInstanceOf(RuntimeException::class.java)

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

      activitiesService.createActivity(aDomainEvent())

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
  inner class UpdateActivitySchedule {

    private fun aDomainEvent() =
      ScheduleDomainEvent(
        eventType = "activities.activity-schedule.amended",
        additionalInformation = ScheduleAdditionalInformation(activityScheduleId),
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

      verify(activitiesApiService).getActivitySchedule(activityScheduleId)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to activityScheduleId.toString()))
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

      verify(activitiesApiService).getActivity(activityId)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to activityScheduleId.toString()))
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

      verify(mappingService).getMappingGivenActivityScheduleId(activityScheduleId)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to activityScheduleId.toString()))
        },
        isNull()
      )
    }

    @Test
    fun `should throw and raise telemetry if fail to update Nomis`() {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(ActivityMappingDto(nomisCourseActivityId, activityScheduleId, "ACTIVITY_CREATED", LocalDateTime.now()))
      whenever(nomisApiService.updateActivity(anyLong(), any()))
        .thenThrow(WebClientResponseException.ServiceUnavailable::class.java)

      assertThatThrownBy {
        activitiesService.updateActivity(aDomainEvent())
      }.isInstanceOf(WebClientResponseException.ServiceUnavailable::class.java)

      verify(nomisApiService).updateActivity(eq(nomisCourseActivityId), any())
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to activityScheduleId.toString()))
        },
        isNull()
      )
    }

    @Test
    fun `should raise telemetry when update of Nomis successful`() {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule(endDate = LocalDate.now().plusDays(1)))
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(ActivityMappingDto(nomisCourseActivityId, activityScheduleId, "ACTIVITY_CREATED", LocalDateTime.now()))

      activitiesService.updateActivity(aDomainEvent())

      verify(nomisApiService).updateActivity(
        eq(nomisCourseActivityId),
        check {
          assertThat(it.endDate).isEqualTo(LocalDate.now().plusDays(1))
          assertThat(it.internalLocationId).isEqualTo(345)
          assertThat(it.payRates).containsExactlyElementsOf(listOf(PayRateRequest("BAS", "1", BigDecimal.valueOf(1.5).setScale(2))))
        }
      )
      verify(telemetryClient).trackEvent(
        eq("activity-amend-event"),
        check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to activityScheduleId.toString()))
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

private fun newActivitySchedule(endDate: LocalDate? = null): ActivitySchedule = ActivitySchedule(
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
  endDate = endDate,
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
  pay = listOf(
    ActivityPay(
      id = 1,
      prisonPayBand = PrisonPayBand(2, 1, "", "", 1, "MDI"),
      incentiveLevel = "BAS",
      rate = 150
    )
  ),
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
    payBandId = 1,
    scheduleDescription = "description",
  )
}
