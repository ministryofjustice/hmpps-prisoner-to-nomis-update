@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityCategory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityLite
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivityMinimumEducationLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ActivitySchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.InternalLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.ScheduledInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

private const val ACTIVITY_SCHEDULE_ID: Long = 100
private const val ACTIVITY_ID: Long = 200
private const val NOMIS_COURSE_ACTIVITY_ID: Long = 300

class SchedulesServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val schedulesService = SchedulesService(activitiesApiService, nomisApiService, mappingService, telemetryClient)

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
      whenever(activitiesApiService.getActivitySchedule(ArgumentMatchers.anyLong()))
        .thenThrow(WebClientResponseException.NotFound::class.java)

      assertThrows<WebClientResponseException.NotFound> {
        schedulesService.updateScheduleInstances(aDomainEvent())
      }

      verify(activitiesApiService).getActivitySchedule(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("schedule-instances-amend-failed"),
        org.mockito.kotlin.check<Map<String, String>> {
          assertThat(it).containsAllEntriesOf(mapOf("activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()))
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if cannot find mappings`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(ArgumentMatchers.anyLong())).thenReturn(newActivitySchedule())
      whenever(mappingService.getMappingGivenActivityScheduleId(ArgumentMatchers.anyLong()))
        .thenThrow(WebClientResponseException.NotFound::class.java)

      assertThrows<WebClientResponseException.NotFound> {
        schedulesService.updateScheduleInstances(aDomainEvent())
      }

      verify(mappingService).getMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("schedule-instances-amend-failed"),
        org.mockito.kotlin.check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf("activityScheduleId" to ACTIVITY_SCHEDULE_ID.toString()),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to update Nomis`() = runTest {
      whenever(activitiesApiService.getActivitySchedule(ArgumentMatchers.anyLong())).thenReturn(newActivitySchedule())
      whenever(mappingService.getMappingGivenActivityScheduleId(ArgumentMatchers.anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          LocalDateTime.now(),
        ),
      )
      whenever(nomisApiService.updateScheduleInstances(ArgumentMatchers.anyLong(), ArgumentMatchers.anyList()))
        .thenThrow(WebClientResponseException.ServiceUnavailable::class.java)

      assertThrows<WebClientResponseException.ServiceUnavailable> {
        schedulesService.updateScheduleInstances(aDomainEvent())
      }

      verify(nomisApiService).updateScheduleInstances(eq(NOMIS_COURSE_ACTIVITY_ID), any())
      verify(telemetryClient).trackEvent(
        eq("schedule-instances-amend-failed"),
        org.mockito.kotlin.check<Map<String, String>> {
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
      whenever(activitiesApiService.getActivitySchedule(ArgumentMatchers.anyLong())).thenReturn(newActivitySchedule(endDate = LocalDate.now().plusDays(1)))
      whenever(mappingService.getMappingGivenActivityScheduleId(ArgumentMatchers.anyLong())).thenReturn(
        ActivityMappingDto(
          NOMIS_COURSE_ACTIVITY_ID,
          ACTIVITY_SCHEDULE_ID,
          "ACTIVITY_CREATED",
          LocalDateTime.now(),
        ),
      )

      schedulesService.updateScheduleInstances(aDomainEvent())

      verify(nomisApiService).updateScheduleInstances(
        eq(NOMIS_COURSE_ACTIVITY_ID),
        org.mockito.kotlin.check {
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
        org.mockito.kotlin.check<Map<String, String>> {
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
