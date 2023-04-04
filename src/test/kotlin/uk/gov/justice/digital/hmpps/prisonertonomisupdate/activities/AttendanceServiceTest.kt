@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException.BadGateway
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceSync
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateAttendanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

class AttendanceServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val attendanceService =
    AttendanceService(activitiesApiService, nomisApiService, mappingService, telemetryClient)

  @Nested
  inner class CreateAttendance {
    @Test
    fun `should throw and raise telemetry if fails to retrieve activity's attendance details`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenThrow(BadGateway::class.java)

      assertThrows<BadGateway> {
        attendanceService.createAttendance(attendanceEvent())
      }

      verify(activitiesApiService).getAttendanceSync(1)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(mapOf("attendanceId" to "1"))
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to retrieve mapping details`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSync())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenThrow(Forbidden::class.java)

      assertThrows<Forbidden> {
        attendanceService.createAttendance(attendanceEvent())
      }

      verify(mappingService).getMappingGivenActivityScheduleId(3)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsAllEntriesOf(
            mapOf(
              "scheduleInstanceId" to "2",
              "activityScheduleId" to "3",
              "prisonerNumber" to "A1234AB",
              "bookingId" to "4",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to create attendance in Nomis`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSync())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.createAttendance(anyLong(), anyLong(), any())).thenThrow(BadRequest::class.java)

      assertThrows<BadRequest> {
        attendanceService.createAttendance(attendanceEvent())
      }

      verify(nomisApiService).createAttendance(eq(5), eq(4), any())
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsAllEntriesOf(
            mapOf(
              "nomisCourseActivityId" to "5",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to map event status`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSync().copy(status = "INVALID"))
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())

      assertThrows<InvalidAttendanceStatusException> {
        attendanceService.createAttendance(attendanceEvent())
      }.also {
        assertThat(it.message).contains("INVALID")
      }

      verifyNoInteractions(nomisApiService)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "attendanceId" to "1",
              "scheduleInstanceId" to "2",
              "activityScheduleId" to "3",
              "prisonerNumber" to "A1234AB",
              "bookingId" to "4",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "5",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to map event outcome`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSync().copy(attendanceReasonCode = "INVALID"))
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())

      assertThrows<InvalidAttendanceReasonException> {
        attendanceService.createAttendance(attendanceEvent())
      }.also {
        assertThat(it.message).contains("INVALID")
      }

      verifyNoInteractions(nomisApiService)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "attendanceId" to "1",
              "scheduleInstanceId" to "2",
              "activityScheduleId" to "3",
              "prisonerNumber" to "A1234AB",
              "bookingId" to "4",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "5",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should create attendance in Nomis and raise telemetry`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSync())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.createAttendance(anyLong(), anyLong(), any())).thenReturn(createAttendanceResponse())

      assertDoesNotThrow {
        attendanceService.createAttendance(attendanceEvent())
      }

      verify(nomisApiService).createAttendance(
        eq(5),
        eq(4),
        check {
          assertThat(it.eventStatusCode).isEqualTo("SCH")
          assertThat(it.eventOutcomeCode).isNull()
          assertThat(it.comments).isNull()
          assertThat(it.unexcusedAbsence).isFalse()
          assertThat(it.authorisedAbsence).isFalse()
          assertThat(it.paid).isFalse()
          assertThat(it.bonusPay).isNull()
        },
      )
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-success"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "attendanceId" to "1",
              "scheduleInstanceId" to "2",
              "activityScheduleId" to "3",
              "prisonerNumber" to "A1234AB",
              "bookingId" to "4",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "5",
              "attendanceEventId" to "6",
            ),
          )
        },
        isNull(),
      )
    }

    private fun attendanceEvent() = AttendanceDomainEvent(
      eventType = "activities.prisoner.attendance-created",
      additionalInformation = AttendanceAdditionalInformation(1),
      version = "1.0",
      description = "some description",
      occurredAt = LocalDateTime.now(),
    )

    private fun attendanceSync() = AttendanceSync(
      attendanceId = 1,
      scheduledInstanceId = 2,
      activityScheduleId = 3,
      sessionDate = LocalDate.now().plusDays(1),
      sessionStartTime = "10:00",
      sessionEndTime = "11:00",
      prisonerNumber = "A1234AB",
      bookingId = 4,
      status = "WAITING",
    )

    private fun activityMappingDto() = ActivityMappingDto(
      nomisCourseActivityId = 5,
      activityScheduleId = 3,
      mappingType = "ACTIVITY_CREATED",
    )

    private fun createAttendanceResponse() = CreateAttendanceResponse(6)
  }

  @Nested
  inner class ToEventOutcome {
    @ParameterizedTest
    @CsvSource(
      value = [
        "ATTENDED,true,ATT,false,false",
        "ATTENDED,false,UNBEH,false,false",
        "CANCELLED,true,CANC,false,true",
        "SUSPENDED,false,SUS,false,true",
        "SICK,true,ACCAB,false,true",
        "SICK,false,REST,false,true",
        "REFUSED,false,UNACAB,true,false",
        "NOT_REQUIRED,true,ACCAB,false,true",
        "REST,false,REST,false,true",
        "REST,true,ACCAB,false,true",
        "CLASH,true,ACCAB,false,true",
        "OTHER,true,ACCAB,false,true",
        "OTHER,false,UNACAB,true,false",
      ],
    )
    fun `should map to Nomis outcome code`(
      attendanceReasonCode: String,
      paid: Boolean,
      expectedNomisCode: String,
      unexcusedAbsence: Boolean,
      authorisedAbsence: Boolean,
    ) {
      val eventOutcome = attendanceSync(attendanceReasonCode, paid).toEventOutcome()
      assertThat(eventOutcome?.code).isEqualTo(expectedNomisCode)
      assertThat(eventOutcome?.unexcusedAbsence).isEqualTo(unexcusedAbsence)
      assertThat(eventOutcome?.authorisedAbsence).isEqualTo(authorisedAbsence)
    }

    @Test
    fun `should throw if we are unable to map the code`() {
      assertThrows<InvalidAttendanceReasonException> {
        attendanceSync("INVALID_CODE", paid = true).toEventOutcome()
      }.also {
        assertThat(it.message).contains("Unable to handle attendance reason code=INVALID_CODE and paid=true")
      }
    }

    private fun attendanceSync(attendanceReasonCode: String, paid: Boolean) = AttendanceSync(
      attendanceId = 0,
      scheduledInstanceId = 0,
      activityScheduleId = 0,
      sessionDate = LocalDate.now(),
      sessionStartTime = "",
      sessionEndTime = "",
      prisonerNumber = "",
      bookingId = 0,
      attendanceReasonCode = attendanceReasonCode,
      status = "",
      issuePayment = paid,
    )
  }

  @Nested
  inner class ToEventStatus {
    @ParameterizedTest
    @CsvSource(
      value = [
        "WAITING,null,SCH",
        "COMPLETED,CANCELLED,CANC",
        "COMPLETED,null,COMP",
        "LOCKED,null,EXP",
      ],
    )
    fun `should mp to Nomis event status code`(
      status: String,
      attendanceReasonCode: String?,
      expectedNomisStatus: String,
    ) {
      val eventStatus = attendanceSync(status, attendanceReasonCode).toEventStatus()
      assertThat(eventStatus).isEqualTo(expectedNomisStatus)
    }

    @Test
    fun `should throw if we are unable to map the status`() {
      assertThrows<InvalidAttendanceStatusException> {
        attendanceSync("INVALID", null).toEventStatus()
      }.also {
        assertThat(it.message).contains("Unable to handle attendance status code=INVALID and attendance reason code=null")
      }
    }

    private fun attendanceSync(status: String, attendanceReasonCode: String?) = AttendanceSync(
      attendanceId = 0,
      scheduledInstanceId = 0,
      activityScheduleId = 0,
      sessionDate = LocalDate.now(),
      sessionStartTime = "",
      sessionEndTime = "",
      prisonerNumber = "",
      bookingId = 0,
      attendanceReasonCode = attendanceReasonCode,
      status = status,
      issuePayment = false,
    )
  }
}
