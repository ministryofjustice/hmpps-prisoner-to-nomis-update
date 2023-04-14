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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException.BadGateway
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AttendanceSync
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.GetAttendanceStatusResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpsertAttendanceResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

private const val ATTENDANCE_ID = 1L
private const val SCHEDULE_INSTANCE_ID = 2L
private const val ACTIVITY_SCHEDULE_ID = 3L
private const val NOMIS_BOOKING_ID = 4L
private const val NOMIS_PRISONER_NUMBER = "A1234BC"
private const val NOMIS_CRS_ACTY_ID = 5L
private const val NOMIS_CRS_SCH_ID = 6L
private const val NOMIS_EVENT_ID = 7L

class AttendanceServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val attendanceService =
    AttendanceService(activitiesApiService, nomisApiService, mappingService, telemetryClient)

  @Nested
  inner class UpsertAttendance {
    @Test
    fun `should throw and raise telemetry if fails to retrieve activity's attendance details`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenThrow(BadGateway::class.java)

      assertThrows<BadGateway> {
        attendanceService.upsertAttendance(attendanceEvent())
      }

      verify(activitiesApiService).getAttendanceSync(1)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(mapOf("attendanceId" to "$ATTENDANCE_ID"))
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to retrieve mapping details`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncWaiting())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenThrow(Forbidden::class.java)

      assertThrows<Forbidden> {
        attendanceService.upsertAttendance(attendanceEvent())
      }

      verify(mappingService).getMappingGivenActivityScheduleId(3)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsAllEntriesOf(
            mapOf(
              "scheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "activityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "prisonerNumber" to NOMIS_PRISONER_NUMBER,
              "bookingId" to "$NOMIS_BOOKING_ID",
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
    fun `should throw and raise telemetry if fails to upsert attendance in Nomis`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncWaiting())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.upsertAttendance(anyLong(), anyLong(), any())).thenThrow(BadRequest::class.java)

      assertThrows<BadRequest> {
        attendanceService.upsertAttendance(attendanceEvent())
      }

      verify(nomisApiService).upsertAttendance(eq(NOMIS_CRS_ACTY_ID), eq(NOMIS_BOOKING_ID), any())
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsAllEntriesOf(
            mapOf(
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to map event status`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncWaiting().copy(status = "INVALID"))
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())

      assertThrows<InvalidAttendanceStatusException> {
        attendanceService.upsertAttendance(attendanceEvent())
      }.also {
        assertThat(it.message).contains("INVALID")
      }

      verifyNoInteractions(nomisApiService)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "attendanceId" to "$ATTENDANCE_ID",
              "scheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "activityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "prisonerNumber" to NOMIS_PRISONER_NUMBER,
              "bookingId" to "$NOMIS_BOOKING_ID",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to map event outcome`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncWaiting().copy(attendanceReasonCode = "INVALID"))
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())

      assertThrows<InvalidAttendanceReasonException> {
        attendanceService.upsertAttendance(attendanceEvent())
      }.also {
        assertThat(it.message).contains("INVALID")
      }

      verifyNoInteractions(nomisApiService)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "attendanceId" to "$ATTENDANCE_ID",
              "scheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "activityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "prisonerNumber" to NOMIS_PRISONER_NUMBER,
              "bookingId" to "$NOMIS_BOOKING_ID",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should create attendance in Nomis and raise telemetry for create request`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncWaiting())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.upsertAttendance(anyLong(), anyLong(), any())).thenReturn(upsertAttendanceResponse())

      assertDoesNotThrow {
        attendanceService.upsertAttendance(attendanceEvent())
      }

      verify(nomisApiService).upsertAttendance(
        eq(NOMIS_CRS_ACTY_ID),
        eq(NOMIS_BOOKING_ID),
        check {
          assertThat(it.scheduleDate).isEqualTo(LocalDate.now().plusDays(1))
          assertThat(it.startTime).isEqualTo("10:00")
          assertThat(it.endTime).isEqualTo("11:00")
          assertThat(it.eventStatusCode).isEqualTo("SCH")
          assertThat(it.eventOutcomeCode).isNull()
          assertThat(it.comments).isNull()
          assertThat(it.unexcusedAbsence).isFalse()
          assertThat(it.authorisedAbsence).isFalse()
          assertThat(it.paid).isFalse()
          assertThat(it.bonusPay).isNull()
        },
      )
      verify(nomisApiService, never()).getAttendanceStatus(anyLong(), anyLong(), any())
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-success"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "attendanceId" to "$ATTENDANCE_ID",
              "scheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "activityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "prisonerNumber" to NOMIS_PRISONER_NUMBER,
              "bookingId" to "$NOMIS_BOOKING_ID",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
              "attendanceEventId" to "$NOMIS_EVENT_ID",
              "nomisCourseScheduleId" to "$NOMIS_CRS_SCH_ID",
              "created" to "true",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should update attendance in Nomis and raise telemetry for update request`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncAttended())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.upsertAttendance(anyLong(), anyLong(), any())).thenReturn(upsertAttendanceResponse(created = false))

      assertDoesNotThrow {
        attendanceService.upsertAttendance(attendanceEvent("activities.prisoner.attendance-amended"))
      }

      verify(nomisApiService).upsertAttendance(
        eq(NOMIS_CRS_ACTY_ID),
        eq(NOMIS_BOOKING_ID),
        check {
          assertThat(it.scheduleDate).isEqualTo(LocalDate.now().plusDays(1))
          assertThat(it.startTime).isEqualTo("10:00")
          assertThat(it.endTime).isEqualTo("11:00")
          assertThat(it.eventStatusCode).isEqualTo("COMP")
          assertThat(it.eventOutcomeCode).isEqualTo("ATT")
          assertThat(it.comments).isEqualTo("Attended")
          assertThat(it.unexcusedAbsence).isFalse()
          assertThat(it.authorisedAbsence).isFalse()
          assertThat(it.paid).isTrue()
          assertThat(it.bonusPay).isEqualTo(BigDecimal(100).setScale(3, RoundingMode.HALF_UP))
        },
      )
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-update-success"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "attendanceId" to "$ATTENDANCE_ID",
              "scheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "activityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "prisonerNumber" to NOMIS_PRISONER_NUMBER,
              "bookingId" to "$NOMIS_BOOKING_ID",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
              "attendanceEventId" to "$NOMIS_EVENT_ID",
              "nomisCourseScheduleId" to "$NOMIS_CRS_SCH_ID",
              "created" to "false",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should get the Nomis attendance if Activities status is now locked`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncLocked())
      whenever(mappingService.getMappingGivenActivityScheduleId(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.upsertAttendance(anyLong(), anyLong(), any())).thenReturn(upsertAttendanceResponse())
      whenever(nomisApiService.getAttendanceStatus(anyLong(), anyLong(), any())).thenReturn(getAttendanceStatusResponse())

      assertDoesNotThrow {
        attendanceService.upsertAttendance(attendanceEvent())
      }

      verify(nomisApiService).getAttendanceStatus(
        eq(NOMIS_CRS_ACTY_ID),
        eq(NOMIS_BOOKING_ID),
        check {
          assertThat(it.scheduleDate).isEqualTo(LocalDate.now().plusDays(1))
          assertThat(it.startTime).isEqualTo("10:00")
          assertThat(it.endTime).isEqualTo("11:00")
        },
      )
      verify(nomisApiService).upsertAttendance(
        eq(NOMIS_CRS_ACTY_ID),
        eq(NOMIS_BOOKING_ID),
        check {
          assertThat(it.eventStatusCode).isEqualTo("EXP")
        },
      )
    }

    private fun attendanceEvent(eventType: String = "activities.prisoner.attendance-created") = AttendanceDomainEvent(
      eventType = eventType,
      additionalInformation = AttendanceAdditionalInformation(ATTENDANCE_ID),
      version = "1.0",
      description = "some description",
      occurredAt = LocalDateTime.now(),
    )

    private fun attendanceSyncWaiting() = AttendanceSync(
      attendanceId = ATTENDANCE_ID,
      scheduledInstanceId = SCHEDULE_INSTANCE_ID,
      activityScheduleId = ACTIVITY_SCHEDULE_ID,
      sessionDate = LocalDate.now().plusDays(1),
      sessionStartTime = "10:00",
      sessionEndTime = "11:00",
      prisonerNumber = NOMIS_PRISONER_NUMBER,
      bookingId = NOMIS_BOOKING_ID,
      status = "WAITING",
    )

    private fun attendanceSyncLocked() = AttendanceSync(
      attendanceId = ATTENDANCE_ID,
      scheduledInstanceId = SCHEDULE_INSTANCE_ID,
      activityScheduleId = ACTIVITY_SCHEDULE_ID,
      sessionDate = LocalDate.now().plusDays(1),
      sessionStartTime = "10:00",
      sessionEndTime = "11:00",
      prisonerNumber = NOMIS_PRISONER_NUMBER,
      bookingId = NOMIS_BOOKING_ID,
      status = "LOCKED",
    )

    private fun attendanceSyncAttended() = AttendanceSync(
      attendanceId = ATTENDANCE_ID,
      scheduledInstanceId = SCHEDULE_INSTANCE_ID,
      activityScheduleId = ACTIVITY_SCHEDULE_ID,
      sessionDate = LocalDate.now().plusDays(1),
      sessionStartTime = "10:00",
      sessionEndTime = "11:00",
      prisonerNumber = NOMIS_PRISONER_NUMBER,
      bookingId = NOMIS_BOOKING_ID,
      status = "COMPLETED",
      attendanceReasonCode = "ATTENDED",
      comment = "Attended",
      issuePayment = true,
      bonusAmount = 100,
    )

    private fun activityMappingDto() = ActivityMappingDto(
      nomisCourseActivityId = NOMIS_CRS_ACTY_ID,
      activityScheduleId = ACTIVITY_SCHEDULE_ID,
      mappingType = "ACTIVITY_CREATED",
    )

    private fun upsertAttendanceResponse(created: Boolean = true) = UpsertAttendanceResponse(
      eventId = NOMIS_EVENT_ID,
      courseScheduleId = NOMIS_CRS_SCH_ID,
      created = created,
    )

    private fun getAttendanceStatusResponse() = GetAttendanceStatusResponse("SCH")
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
        "NOT_REQUIRED,true,NREQ,false,true",
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
        "WAITING,,,SCH",
        "COMPLETED,CANCELLED,,CANC",
        "COMPLETED,,,COMP",
        "LOCKED,,,EXP",
        "LOCKED,,SCH,EXP",
        "LOCKED,,COMP,COMP",
      ],
    )
    fun `should mp to Nomis event status code`(
      status: String,
      attendanceReasonCode: String?,
      currentNomisAttendanceStatus: String?,
      expectedNomisStatus: String,
    ) {
      val eventStatus = attendanceSync(status, attendanceReasonCode).toEventStatus(currentNomisAttendanceStatus)
      assertThat(eventStatus).isEqualTo(expectedNomisStatus)
    }

    @Test
    fun `should throw if we are unable to map the status`() {
      assertThrows<InvalidAttendanceStatusException> {
        attendanceSync("INVALID", null).toEventStatus(null)
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
