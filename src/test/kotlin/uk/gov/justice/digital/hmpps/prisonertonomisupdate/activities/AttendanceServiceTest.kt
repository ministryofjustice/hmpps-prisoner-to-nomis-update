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
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAttendanceResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class AttendanceServiceTest {

  private val activitiesApiService: ActivitiesApiService = mock()
  private val nomisApiService: ActivitiesNomisApiService = mock()
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
        attendanceService.upsertAttendanceEvent(attendanceEvent())
      }

      verify(activitiesApiService).getAttendanceSync(ATTENDANCE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(mapOf("dpsAttendanceId" to "$ATTENDANCE_ID"))
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to retrieve mapping details`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncWaiting())
      whenever(mappingService.getMappings(anyLong())).thenThrow(Forbidden::class.java)

      assertThrows<Forbidden> {
        attendanceService.upsertAttendanceEvent(attendanceEvent())
      }

      verify(mappingService).getMappings(ACTIVITY_SCHEDULE_ID)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsAllEntriesOf(
            mapOf(
              "dpsScheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "dpsActivityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "offenderNo" to OFFENDER_NO,
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
      whenever(mappingService.getMappings(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.upsertAttendance(anyLong(), anyLong(), any())).thenThrow(BadRequest::class.java)

      assertThrows<BadRequest> {
        attendanceService.upsertAttendanceEvent(attendanceEvent())
      }

      verify(nomisApiService).upsertAttendance(eq(NOMIS_CRS_SCH_ID), eq(NOMIS_BOOKING_ID), any())
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
      whenever(mappingService.getMappings(anyLong())).thenReturn(activityMappingDto())

      assertThrows<InvalidAttendanceStatusException> {
        attendanceService.upsertAttendanceEvent(attendanceEvent())
      }.also {
        assertThat(it.message).contains("INVALID")
      }

      verifyNoInteractions(nomisApiService)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsAttendanceId" to "$ATTENDANCE_ID",
              "dpsScheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "dpsActivityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "offenderNo" to OFFENDER_NO,
              "bookingId" to "$NOMIS_BOOKING_ID",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
              "nomisCourseScheduleId" to "$NOMIS_CRS_SCH_ID",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should throw and raise telemetry if fails to map event outcome`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncWaiting().copy(attendanceReasonCode = "INVALID"))
      whenever(mappingService.getMappings(anyLong())).thenReturn(activityMappingDto())

      assertThrows<InvalidAttendanceReasonException> {
        attendanceService.upsertAttendanceEvent(attendanceEvent())
      }.also {
        assertThat(it.message).contains("INVALID")
      }

      verifyNoInteractions(nomisApiService)
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-failed"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsAttendanceId" to "$ATTENDANCE_ID",
              "dpsScheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "dpsActivityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "offenderNo" to OFFENDER_NO,
              "bookingId" to "$NOMIS_BOOKING_ID",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
              "nomisCourseScheduleId" to "$NOMIS_CRS_SCH_ID",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should create attendance in Nomis and raise telemetry for create request`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncWaiting())
      whenever(mappingService.getMappings(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.upsertAttendance(anyLong(), anyLong(), any())).thenReturn(upsertAttendanceResponse())

      assertDoesNotThrow {
        attendanceService.upsertAttendanceEvent(attendanceEvent())
      }

      verify(nomisApiService).upsertAttendance(
        eq(NOMIS_CRS_SCH_ID),
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
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-create-success"),
        check<MutableMap<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsAttendanceId" to "$ATTENDANCE_ID",
              "dpsScheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "dpsActivityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "offenderNo" to OFFENDER_NO,
              "bookingId" to "$NOMIS_BOOKING_ID",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
              "nomisAttendanceEventId" to "$NOMIS_EVENT_ID",
              "nomisCourseScheduleId" to "$NOMIS_CRS_SCH_ID",
              "created" to "true",
              "prisonId" to "MDI",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should update attendance in Nomis and raise telemetry for update request`() = runTest {
      whenever(activitiesApiService.getAttendanceSync(anyLong())).thenReturn(attendanceSyncAttended())
      whenever(mappingService.getMappings(anyLong())).thenReturn(activityMappingDto())
      whenever(nomisApiService.upsertAttendance(anyLong(), anyLong(), any())).thenReturn(upsertAttendanceResponse(created = false))

      assertDoesNotThrow {
        attendanceService.upsertAttendanceEvent(attendanceEvent("activities.prisoner.attendance-amended"))
      }

      verify(nomisApiService).upsertAttendance(
        eq(NOMIS_CRS_SCH_ID),
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
              "dpsAttendanceId" to "$ATTENDANCE_ID",
              "dpsScheduleInstanceId" to "$SCHEDULE_INSTANCE_ID",
              "dpsActivityScheduleId" to "$ACTIVITY_SCHEDULE_ID",
              "offenderNo" to OFFENDER_NO,
              "bookingId" to "$NOMIS_BOOKING_ID",
              "sessionDate" to LocalDate.now().plusDays(1).toString(),
              "sessionStartTime" to "10:00",
              "sessionEndTime" to "11:00",
              "nomisCourseActivityId" to "$NOMIS_CRS_ACTY_ID",
              "nomisAttendanceEventId" to "$NOMIS_EVENT_ID",
              "nomisCourseScheduleId" to "$NOMIS_CRS_SCH_ID",
              "created" to "false",
              "prisonId" to "MDI",
            ),
          )
        },
        isNull(),
      )
    }

    private fun attendanceEvent(eventType: String = "activities.prisoner.attendance-created") = AttendanceDomainEvent(
      eventType = eventType,
      additionalInformation = AttendanceAdditionalInformation(ATTENDANCE_ID),
      version = "1.0",
      description = "some description",
    )

    private fun attendanceSyncWaiting() = AttendanceSync(
      attendanceId = ATTENDANCE_ID,
      scheduledInstanceId = SCHEDULE_INSTANCE_ID,
      activityScheduleId = ACTIVITY_SCHEDULE_ID,
      sessionDate = LocalDate.now().plusDays(1),
      sessionStartTime = "10:00",
      sessionEndTime = "11:00",
      prisonerNumber = OFFENDER_NO,
      bookingId = NOMIS_BOOKING_ID,
      status = "WAITING",
    )

    private fun attendanceSyncAttended() = AttendanceSync(
      attendanceId = ATTENDANCE_ID,
      scheduledInstanceId = SCHEDULE_INSTANCE_ID,
      activityScheduleId = ACTIVITY_SCHEDULE_ID,
      sessionDate = LocalDate.now().plusDays(1),
      sessionStartTime = "10:00",
      sessionEndTime = "11:00",
      prisonerNumber = OFFENDER_NO,
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
      activityId = ACTIVITY_ID,
      mappingType = "ACTIVITY_CREATED",
      scheduledInstanceMappings = listOf(
        ActivityScheduleMappingDto(
          scheduledInstanceId = SCHEDULE_INSTANCE_ID,
          nomisCourseScheduleId = NOMIS_CRS_SCH_ID,
          mappingType = "ACTIVITY_CREATED",
        ),
      ),
    )

    private fun upsertAttendanceResponse(created: Boolean = true) = UpsertAttendanceResponse(
      eventId = NOMIS_EVENT_ID,
      courseScheduleId = NOMIS_CRS_SCH_ID,
      created = created,
      prisonId = "MDI",
    )
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
        "AUTO_SUSPENDED,false,SUS,false,true",
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
    @MethodSource("uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.AttendanceServiceTestKt#eventStatusTests")
    fun `should map to Nomis event status code`(
      status: String,
      attendanceReasonCode: String?,
      sessionDate: LocalDate,
      expectedNomisStatus: String,
    ) {
      val eventStatus = attendanceSync(status, attendanceReasonCode, sessionDate).toEventStatus()
      assertThat(eventStatus).isEqualTo(expectedNomisStatus)
    }

    @Test
    fun `should throw if we are unable to map the status`() {
      assertThrows<InvalidAttendanceStatusException> {
        attendanceSync("INVALID", null, LocalDate.now()).toEventStatus()
      }.also {
        assertThat(it.message).contains("Unable to handle attendance status code=INVALID and attendance reason code=null")
      }
    }

    private fun attendanceSync(status: String, attendanceReasonCode: String?, sessionDate: LocalDate) = AttendanceSync(
      attendanceId = 0,
      scheduledInstanceId = 0,
      activityScheduleId = 0,
      sessionDate = sessionDate,
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

private fun eventStatusTests(): List<Arguments> = listOf(
  Arguments.of("WAITING", null, LocalDate.now(), "SCH"),
  Arguments.of("WAITING", null, LocalDate.now().minusDays(1), "EXP"),
  Arguments.of("COMPLETED", "CANCELLED", LocalDate.now(), "CANC"),
  Arguments.of("COMPLETED", null, LocalDate.now().minusDays(1), "COMP"),
)
