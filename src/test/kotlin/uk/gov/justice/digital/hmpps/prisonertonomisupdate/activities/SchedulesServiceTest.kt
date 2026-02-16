@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
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
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityCategory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityLite
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleInstance.TimeSlot.AM
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityScheduleLite
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCourseScheduleResponse
import java.time.LocalDate
import java.time.LocalDateTime

class SchedulesServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: ActivitiesNomisApiService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val schedulesService = SchedulesService(activitiesApiService, nomisApiService, mappingService, telemetryClient)

  @Nested
  inner class AmendScheduledInstance {

    private fun aDomainEvent() = ScheduledInstanceDomainEvent(
      eventType = "activities.scheduled-instance.amended",
      additionalInformation = ScheduledInstanceAdditionalInformation(SCHEDULE_INSTANCE_ID),
      version = "1.0",
      description = "description",
    )

    @Test
    fun `should throw and raise telemetry if cannot load Activity Schedule`() = runTest {
      whenever(activitiesApiService.getScheduledInstance(anyLong()))
        .thenThrow(WebClientResponseException.NotFound::class.java)

      assertThrows<WebClientResponseException.NotFound> {
        schedulesService.updateScheduledInstance(aDomainEvent())
      }

      verify(activitiesApiService).getScheduledInstance(SCHEDULE_INSTANCE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-scheduled-instance-amend-failed"),
        check<Map<String, String>> {
          assertThat(it["dpsScheduledInstanceId"]).isEqualTo(SCHEDULE_INSTANCE_ID.toString())
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot find mappings`() = runTest {
      whenever(activitiesApiService.getScheduledInstance(anyLong())).thenReturn(newScheduledInstance())
      whenever(mappingService.getMappings(anyLong()))
        .thenThrow(WebClientResponseException.NotFound::class.java)

      assertThrows<WebClientResponseException.NotFound> {
        schedulesService.updateScheduledInstance(aDomainEvent())
      }

      verify(mappingService).getMappings(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-scheduled-instance-amend-failed"),
        check<Map<String, String>> {
          assertThat(it["scheduleDate"]).isEqualTo("2023-02-23")
          assertThat(it["startTime"]).isEqualTo("08:00")
          assertThat(it["endTime"]).isEqualTo("11:00")
          assertThat(it["prisonId"]).isEqualTo("LEI")
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot find scheduled instance mapping`() = runTest {
      whenever(activitiesApiService.getScheduledInstance(anyLong())).thenReturn(newScheduledInstance())
      whenever(mappingService.getMappings(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_CRS_ACTY_ID,
          ACTIVITY_SCHEDULE_ID,
          ACTIVITY_ID,
          "ACTIVITY_CREATED",
          listOf(),
          LocalDateTime.now(),
        ),
      )

      assertThrows<ValidationException> {
        schedulesService.updateScheduledInstance(aDomainEvent())
      }.also {
        assertThat(it.message).isEqualTo("Mapping for Activity's scheduled instance id not found: $SCHEDULE_INSTANCE_ID")
      }

      verify(mappingService).getMappings(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-scheduled-instance-amend-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("$NOMIS_CRS_ACTY_ID")
          assertThat(it["scheduleDate"]).isEqualTo("2023-02-23")
          assertThat(it["startTime"]).isEqualTo("08:00")
          assertThat(it["endTime"]).isEqualTo("11:00")
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to update Nomis`() = runTest {
      whenever(activitiesApiService.getScheduledInstance(anyLong())).thenReturn(newScheduledInstance())
      whenever(mappingService.getMappings(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_CRS_ACTY_ID,
          ACTIVITY_SCHEDULE_ID,
          ACTIVITY_ID,
          "ACTIVITY_CREATED",
          listOf(
            ActivityScheduleMappingDto(
              SCHEDULE_INSTANCE_ID,
              NOMIS_CRS_SCH_ID,
              "ACTIVITY_CREATED",
            ),
          ),
          LocalDateTime.now(),
        ),
      )
      whenever(nomisApiService.updateScheduledInstance(anyLong(), any()))
        .thenThrow(WebClientResponseException.ServiceUnavailable::class.java)

      assertThrows<WebClientResponseException.ServiceUnavailable> {
        schedulesService.updateScheduledInstance(aDomainEvent())
      }

      verify(nomisApiService).updateScheduledInstance(eq(NOMIS_CRS_ACTY_ID), any())
      verify(telemetryClient).trackEvent(
        eq("activity-scheduled-instance-amend-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisCourseActivityId"]).isEqualTo(NOMIS_CRS_ACTY_ID.toString())
          assertThat(it["prisonId"]).isEqualTo("LEI")
        },
        isNull(),
      )
    }

    @Test
    fun `should raise telemetry when update of Nomis successful`() = runTest {
      whenever(activitiesApiService.getScheduledInstance(anyLong())).thenReturn(newScheduledInstance())
      whenever(mappingService.getMappings(anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_CRS_ACTY_ID,
          ACTIVITY_SCHEDULE_ID,
          ACTIVITY_ID,
          "ACTIVITY_CREATED",
          listOf(
            ActivityScheduleMappingDto(
              SCHEDULE_INSTANCE_ID,
              NOMIS_CRS_SCH_ID,
              "ACTIVITY_CREATED",
            ),
          ),
          LocalDateTime.now(),
        ),
      )
      whenever(nomisApiService.updateScheduledInstance(anyLong(), any())).thenReturn(UpdateCourseScheduleResponse(NOMIS_CRS_SCH_ID))

      schedulesService.updateScheduledInstance(aDomainEvent())

      verify(nomisApiService).updateScheduledInstance(
        eq(NOMIS_CRS_ACTY_ID),
        check {
          with(it) {
            assertThat(date).isEqualTo("2023-02-23")
            assertThat(startTime).isEqualTo("08:00")
            assertThat(endTime).isEqualTo("11:00")
          }
        },
      )
      verify(telemetryClient).trackEvent(
        eq("activity-scheduled-instance-amend-success"),
        check<Map<String, String>> {
          assertThat(it["dpsScheduledInstanceId"]).isEqualTo(SCHEDULE_INSTANCE_ID.toString())
          assertThat(it["dpsActivityScheduleId"]).isEqualTo(ACTIVITY_SCHEDULE_ID.toString())
          assertThat(it["nomisCourseActivityId"]).isEqualTo(NOMIS_CRS_ACTY_ID.toString())
          assertThat(it["scheduleDate"]).isEqualTo("2023-02-23")
          assertThat(it["startTime"]).isEqualTo("08:00")
          assertThat(it["endTime"]).isEqualTo("11:00")
          assertThat(it["nomisCourseScheduleId"]).isEqualTo(NOMIS_CRS_SCH_ID.toString())
          assertThat(it["prisonId"]).isEqualTo("LEI")
        },
        isNull(),
      )
    }
  }
}

private fun newScheduledInstance() = ActivityScheduleInstance(
  id = SCHEDULE_INSTANCE_ID,
  date = LocalDate.parse("2023-02-23"),
  startTime = "08:00",
  endTime = "11:00",
  cancelled = true,
  attendances = listOf(),
  cancelledTime = null,
  cancelledBy = null,
  timeSlot = AM,
  activitySchedule =
  ActivityScheduleLite(
    id = ACTIVITY_SCHEDULE_ID,
    description = "test",
    capacity = 10,
    slots = listOf(),
    startDate = LocalDate.parse("2023-02-01"),
    scheduleWeeks = 1,
    usePrisonRegimeTime = false,
    activity =
    ActivityLite(
      id = ACTIVITY_ID,
      prisonCode = "LEI",
      attendanceRequired = true,
      inCell = false,
      pieceWork = false,
      outsideWork = false,
      payPerSession = ActivityLite.PayPerSession.H,
      summary = "test",
      riskLevel = "risk",
      minimumEducationLevel = listOf(),
      category =
      ActivityCategory(
        id = 123,
        code = "ANY",
        description = "any",
        name = "any",
      ),
      createdTime = LocalDateTime.parse("2023-02-10T08:34:38"),
      activityState = ActivityLite.ActivityState.LIVE,
      allocated = 5,
      capacity = 10,
      onWing = false,
      offWing = false,
      paid = true,
    ),
  ),
  advanceAttendances = listOf(),
)
