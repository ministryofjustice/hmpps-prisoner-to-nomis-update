package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateActivityResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

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
      whenever(activitiesApiService.getActivitySchedule(123)).thenReturn(newActivitySchedule(123))
      whenever(activitiesApiService.getActivity(56)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = 456)
      )

      activitiesService.createActivity(
        ActivitiesService.OutboundHMPPSDomainEvent(
          eventType = "dummy",
          identifier = 123,
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("activity-created-event"),
        org.mockito.kotlin.check {
          assertThat(it["courseActivityId"]).isEqualTo("456")
          assertThat(it["activityScheduleId"]).isEqualTo("123")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
      )
    }

    @Test
    fun `should not update NOMIS if activity already mapped (exists in nomis)`() {
      whenever(activitiesApiService.getActivitySchedule(123)).thenReturn(newActivitySchedule(123))
      whenever(activitiesApiService.getActivity(56)).thenReturn(newActivity())
      whenever(mappingService.getMappingGivenActivityScheduleId(123)).thenReturn(
        ActivityMappingDto(nomisCourseActivityId = 456, activityScheduleId = 12345, mappingType = "A_TYPE")
      )

      activitiesService.createActivity(
        ActivitiesService.OutboundHMPPSDomainEvent(
          eventType = "dummy",
          identifier = 123,
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
        )
      )

      verifyNoInteractions(nomisApiService)
    }

    @Test
    fun `should log a creation failure`() {
      whenever(activitiesApiService.getActivitySchedule(123)).thenReturn(newActivitySchedule(123))
      whenever(activitiesApiService.getActivity(56)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenThrow(RuntimeException("test"))

      assertThatThrownBy {
        activitiesService.createActivity(
          ActivitiesService.OutboundHMPPSDomainEvent(
            eventType = "dummy",
            identifier = 123,
            version = "1.0",
            description = "description",
            occurredAt = LocalDateTime.now(),
          )
        )
      }.isInstanceOf(RuntimeException::class.java)

      verify(telemetryClient).trackEvent(
        eq("activity-create-failed"),
        org.mockito.kotlin.check {
          assertThat(it["activityScheduleId"]).isEqualTo("123")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
      )
    }

    @Test
    fun `should log a mapping creation failure`() {
      whenever(activitiesApiService.getActivitySchedule(123)).thenReturn(newActivitySchedule(123))
      whenever(activitiesApiService.getActivity(56)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = 456)
      )
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      activitiesService.createActivity(
        ActivitiesService.OutboundHMPPSDomainEvent(
          eventType = "dummy",
          identifier = 123,
          version = "1.0",
          description = "description",
          occurredAt = LocalDateTime.now(),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("activity-create-map-failed"),
        org.mockito.kotlin.check {
          assertThat(it["courseActivityId"]).isEqualTo("456")
          assertThat(it["activityScheduleId"]).isEqualTo("123")
          assertThat(it["description"]).isEqualTo("description")
        },
        isNull()
      )
    }
  }

  @Nested
  inner class Retry {

    @Test
    fun `should call mapping service`() {
      activitiesService.createRetry(ActivityContext(nomisCourseActivityId = 456, activityScheduleId = 1234))

      verify(mappingService).createMapping(
        ActivityMappingDto(nomisCourseActivityId = 456, activityScheduleId = 1234, mappingType = "ACTIVITY_CREATED")
      )
    }
  }
}

fun newActivitySchedule(id: Long): ActivitySchedule = ActivitySchedule(
  id = id,
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
    id = 56,
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

fun newActivity(): Activity = Activity(
  id = 56,
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
