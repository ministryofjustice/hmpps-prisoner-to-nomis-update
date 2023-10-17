package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.attendanceMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate

class AttendancesIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class UpsertAttendance {
    @Test
    fun `will consume a create attendance message`() {
      ActivitiesApiExtension.activitiesApi.stubGetAttendanceSync(ATTENDANCE_ID, buildGetAttendanceSyncResponse())
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubUpsertAttendance(NOMIS_CRS_SCH_ID, NOMIS_BOOKING_ID, """{ "eventId": $NOMIS_EVENT_ID, "courseScheduleId": $NOMIS_CRS_SCH_ID }""")

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(attendanceMessagePayload("activities.prisoner.attendance-created", ATTENDANCE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.attendance-created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/synchronisation/attendance/$ATTENDANCE_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance"))
          .withRequestBody(matchingJsonPath("scheduleDate", equalTo(LocalDate.now().plusDays(1).toString())))
          .withRequestBody(matchingJsonPath("startTime", equalTo("10:00")))
          .withRequestBody(matchingJsonPath("endTime", equalTo("11:00")))
          .withRequestBody(matchingJsonPath("eventStatusCode", equalTo("SCH")))
          .withRequestBody(matchingJsonPath("unexcusedAbsence", equalTo("false")))
          .withRequestBody(matchingJsonPath("authorisedAbsence", equalTo("false")))
          .withRequestBody(matchingJsonPath("paid", equalTo("false"))),
      )
    }

    @Test
    fun `will consume an amend attendance message`() {
      ActivitiesApiExtension.activitiesApi.stubGetAttendanceSync(ATTENDANCE_ID, buildGetAttendanceSyncResponse())
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubUpsertAttendance(NOMIS_CRS_ACTY_ID, NOMIS_BOOKING_ID, """{ "eventId": $NOMIS_EVENT_ID, "courseScheduleId": $NOMIS_CRS_SCH_ID }""")

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(attendanceMessagePayload("activities.prisoner.attendance-amended", ATTENDANCE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.attendance-amended").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/synchronisation/attendance/$ATTENDANCE_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance"))
          .withRequestBody(
            matchingJsonPath("scheduleDate", equalTo(LocalDate.now().plusDays(1).toString())),
          )
          .withRequestBody(matchingJsonPath("startTime", equalTo("10:00")))
          .withRequestBody(matchingJsonPath("endTime", equalTo("11:00"))),
      )
    }

    @Test
    fun `will consume an attendance expired message`() {
      ActivitiesApiExtension.activitiesApi.stubGetAttendanceSync(ATTENDANCE_ID, buildGetAttendanceSyncResponse(LocalDate.now().minusDays(1)))
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubUpsertAttendance(NOMIS_CRS_ACTY_ID, NOMIS_BOOKING_ID, """{ "eventId": $NOMIS_EVENT_ID, "courseScheduleId": $NOMIS_CRS_SCH_ID }""")

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(attendanceMessagePayload("activities.prisoner.attendance-expired", ATTENDANCE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.attendance-expired").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/synchronisation/attendance/$ATTENDANCE_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance"))
          .withRequestBody(
            matchingJsonPath("scheduleDate", equalTo(LocalDate.now().minusDays(1).toString())),
          )
          .withRequestBody(matchingJsonPath("eventStatusCode", equalTo("EXP"))),
      )
    }

    @Test
    fun `will push a failed message onto the DLQ`() {
      ActivitiesApiExtension.activitiesApi.stubGetAttendanceSyncWithError(1, 503)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(attendanceMessagePayload("activities.prisoner.attendance-created", ATTENDANCE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.attendance-created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/synchronisation/attendance/$ATTENDANCE_ID") } matches { it == 1 }
      await untilCallTo { awsSqsActivityDlqClient.countMessagesOnQueue(activityDlqUrl).get() } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        0,
        putRequestedFor(urlEqualTo("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance")),
      )
    }

    @Test
    fun `will ignore a bad request where the attendance is paid on the same day as the schedule`() {
      ActivitiesApiExtension.activitiesApi.stubGetAttendanceSync(
        ATTENDANCE_ID,
        buildGetAttendanceSyncResponse(LocalDate.now()),
      )
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubUpsertAttendanceWithError(
        courseScheduleId = NOMIS_CRS_SCH_ID,
        bookingId = NOMIS_BOOKING_ID,
        status = 400,
        body = """
          {
            "status": 400,
            "errorCode": 1001,
            "userMessage": "Bad request: Attendance 1234 cannot be changed after it has already been paid"
          }
        """.trimIndent(),
      )

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(attendanceMessagePayload("activities.prisoner.attendance-amended", ATTENDANCE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.attendance-amended").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/synchronisation/attendance/$ATTENDANCE_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance") } matches { it == 1 }

      // No DLQ message but telemetry is raised
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("activity-attendance-update-ignored"),
          check<Map<String, String>> {
            assertThat(it["reason"]).isEqualTo("Attendance update ignored as already paid session on ${LocalDate.now()} at 10:00")
          },
          isNull(),
        )
      }
      assertThat(awsSqsActivityDlqClient.countMessagesOnQueue(activityDlqUrl).get()).isEqualTo(0)
    }
  }
}

fun buildGetAttendanceSyncResponse(sessionDate: LocalDate = LocalDate.now().plusDays(1)) = """
  {
    "attendanceId": $ATTENDANCE_ID,
    "scheduledInstanceId": $SCHEDULE_INSTANCE_ID,
    "activityScheduleId": $ACTIVITY_SCHEDULE_ID,
    "sessionDate": "$sessionDate",
    "sessionStartTime": "10:00",
    "sessionEndTime": "11:00",
    "prisonerNumber": "A1234AB",
    "bookingId": $NOMIS_BOOKING_ID,
    "status": "WAITING"
  }
""".trimIndent()
