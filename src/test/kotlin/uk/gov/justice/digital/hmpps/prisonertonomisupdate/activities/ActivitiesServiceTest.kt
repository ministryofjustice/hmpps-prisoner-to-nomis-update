@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.PrisonPayBand
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateActivityResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PayRateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

internal class ActivitiesServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val updateQueueService: ActivitiesRetryQueueService = mock()
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
        CreateActivityResponse(courseActivityId = NOMIS_CRS_ACTY_ID, courseSchedules = listOf()),
      )

      activitiesService.createActivityEvent(aDomainEvent())

      verify(telemetryClient).trackEvent(
        eq("activity-create-success"),
        check {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("$NOMIS_CRS_ACTY_ID")
          assertThat(it["dpsActivityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
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
        CreateActivityResponse(courseActivityId = NOMIS_CRS_ACTY_ID, courseSchedules = listOf()),
      )

      activitiesService.createActivityEvent(aDomainEvent())

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
      whenever(mappingService.getMappingsOrNull(ACTIVITY_SCHEDULE_ID)).thenReturn(
        ActivityMappingDto(
          nomisCourseActivityId = NOMIS_CRS_ACTY_ID,
          activityScheduleId = ACTIVITY_SCHEDULE_ID,
          mappingType = "A_TYPE",
        ),
      )

      activitiesService.createActivityEvent(aDomainEvent())

      verifyNoInteractions(nomisApiService)
    }

    @Test
    fun `should log a mapping creation failure`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(ACTIVITY_SCHEDULE_ID)).thenReturn(
        newActivitySchedule(),
      )
      whenever(activitiesApiService.getActivity(ACTIVITY_ID)).thenReturn(newActivity())
      whenever(nomisApiService.createActivity(any())).thenReturn(
        CreateActivityResponse(courseActivityId = NOMIS_CRS_ACTY_ID, courseSchedules = listOf()),
      )
      whenever(mappingService.createMapping(any())).thenThrow(RuntimeException("test"))

      activitiesService.createActivityEvent(aDomainEvent())

      verify(telemetryClient).trackEvent(
        eq("activity-mapping-create-failed"),
        check {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("$NOMIS_CRS_ACTY_ID")
          assertThat(it["dpsActivityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
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
      whenever(mappingService.getMappings(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_CRS_ACTY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          listOf(),
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
          assertThat(it).containsAllEntriesOf(mapOf("dpsActivityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()))
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot load Activity `() = runTest {
      whenever(mappingService.getMappings(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_CRS_ACTY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          listOf(),
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
          assertThat(it["nomisCourseActivityId"]).isEqualTo("$NOMIS_CRS_ACTY_ID")
          assertThat(it["dpsActivityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot find mappings`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(anyLong())).thenReturn(newActivitySchedule())
      whenever(activitiesApiService.getActivity(anyLong())).thenReturn(newActivity())
      whenever(mappingService.getMappings(anyLong()))
        .thenThrow(NotFound::class.java)

      assertThrows<NotFound> {
        activitiesService.updateActivity(aDomainEvent())
      }

      verify(mappingService).getMappings(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsActivityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
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
      whenever(mappingService.getMappings(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_CRS_ACTY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          listOf(),
          LocalDateTime.now(),
        ),
      )
      whenever(nomisApiService.updateActivity(anyLong(), any()))
        .thenThrow(ServiceUnavailable::class.java)

      assertThrows<ServiceUnavailable> {
        activitiesService.updateActivity(aDomainEvent())
      }

      verify(nomisApiService).updateActivity(eq(NOMIS_CRS_ACTY_ID), any())
      verify(telemetryClient).trackEvent(
        eq("activity-amend-failed"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsActivityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "dpsActivityId" to ACTIVITY_ID.toString(),
              "nomisCourseActivityId" to NOMIS_CRS_ACTY_ID.toString(),
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
      whenever(nomisApiService.updateActivity(anyLong(), any())).thenReturn(CreateActivityResponse(1L, listOf()))
      whenever(mappingService.getMappings(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_CRS_ACTY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          listOf(),
          LocalDateTime.now(),
        ),
      )

      activitiesService.updateActivity(aDomainEvent())

      verify(nomisApiService).updateActivity(
        eq(NOMIS_CRS_ACTY_ID),
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
              "dpsActivityScheduleId" to ACTIVITY_SCHEDULE_ID.toString(),
              "dpsActivityId" to ACTIVITY_ID.toString(),
              "nomisCourseActivityId" to NOMIS_CRS_ACTY_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class Retry {

    @Test
    fun `should call mapping service`() = runTest {
      val mappingDto = ActivityMappingDto(
        nomisCourseActivityId = NOMIS_CRS_ACTY_ID,
        activityScheduleId = ACTIVITY_SCHEDULE_ID,
        mappingType = "ACTIVITY_CREATED",
        scheduledInstanceMappings = listOf(
          ActivityScheduleMappingDto(
            scheduledInstanceId = SCHEDULE_INSTANCE_ID,
            nomisCourseScheduleId = NOMIS_CRS_SCH_ID,
            mappingType = "ACTIVITY_CREATED",
          ),
        ),
      )
      activitiesService.createRetry(
        CreateMappingRetryMessage(
          mapping = mappingDto,
          telemetryAttributes = mapOf(),
        ),
      )

      verify(mappingService).createMapping(mappingDto)
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
    payPerSession = ActivityLite.PayPerSession.H,
    minimumIncentiveLevel = "Basic",
    minimumIncentiveNomisCode = "BAS",
    minimumEducationLevel = listOf(
      ActivityMinimumEducationLevel(
        id = 123456,
        educationLevelCode = "Basic",
        educationLevelDescription = "Basic",
        studyAreaCode = "ENGLA",
        studyAreaDescription = "English language",
      ),
    ),
    createdTime = LocalDateTime.parse("2023-02-10T08:34:38"),
    activityState = ActivityLite.ActivityState.LIVE,
    allocated = 5,
    capacity = 10,
    onWing = false,
  ),
  slots = emptyList(),
  startDate = LocalDate.now(),
  endDate = endDate,
  runsOnBankHoliday = true,
  scheduleWeeks = 1,
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
  payPerSession = Activity.PayPerSession.H,
  eligibilityRules = emptyList(),
  schedules = emptyList(),
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
      studyAreaCode = "ENGLA",
      studyAreaDescription = "English language",
    ),
  ),
  onWing = false,
)
