package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.attendanceDeletedMessagePayload
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
      NomisApiExtension.nomisApi.stubUpsertAttendance(NOMIS_CRS_SCH_ID, NOMIS_BOOKING_ID, """{ "eventId": $NOMIS_EVENT_ID, "courseScheduleId": $NOMIS_CRS_SCH_ID }""")

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
      NomisApiExtension.nomisApi.stubUpsertAttendance(NOMIS_CRS_SCH_ID, NOMIS_BOOKING_ID, """{ "eventId": $NOMIS_EVENT_ID, "courseScheduleId": $NOMIS_CRS_SCH_ID }""")

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

    @Test
    fun `will ignore a bad request where the prisoner is deallocated and has moved from the prison`() {
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
            "errorCode": 1002,
            "userMessage": "Bad request: Cannot create an attendance for allocation any_allocation after its end date of any date with prisoner now in location MDI"
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
            assertThat(it["reason"]).isEqualTo("Attendance update ignored as the prisoner is deallocated and has moved from the prison")
          },
          isNull(),
        )
      }
      assertThat(awsSqsActivityDlqClient.countMessagesOnQueue(activityDlqUrl).get()).isEqualTo(0)
    }

    @Test
    fun `will reject the event where an attendance is paid after the schedule date`() {
      ActivitiesApiExtension.activitiesApi.stubGetAttendanceSync(
        ATTENDANCE_ID,
        buildGetAttendanceSyncResponse(LocalDate.now().minusDays(1)),
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

      // We now have a DLQ message and telemetry is raised
      await untilAsserted {
        verify(telemetryClient).trackEvent(eq("activity-attendance-update-failed"), any<Map<String, String>>(), isNull())
      }
      assertThat(awsSqsActivityDlqClient.countMessagesOnQueue(activityDlqUrl).get()).isEqualTo(1)
    }
  }

  @Nested
  inner class DeleteAttendance {
    @Test
    fun `will delete an attendance`() {
      MappingExtension.mappingServer.stubGetScheduleInstanceMapping(SCHEDULE_INSTANCE_ID, buildGetScheduleMappingResponse())
      NomisApiExtension.nomisApi.stubDeleteAttendance(NOMIS_CRS_SCH_ID, NOMIS_BOOKING_ID)

      awsSnsClient.publishAttendanceDeleted()

      assertThat(MappingExtension.mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/activities/schedules/scheduled-instance-id/$SCHEDULE_INSTANCE_ID"))))
      assertThat(NomisApiExtension.nomisApi.verify(deleteRequestedFor(urlEqualTo("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance"))))
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-delete-success"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsScheduledInstanceId" to SCHEDULE_INSTANCE_ID.toString(),
              "bookingId" to NOMIS_BOOKING_ID.toString(),
              "nomisCourseScheduleId" to NOMIS_CRS_SCH_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will send message to the DLQ when mapping API fails`() {
      MappingExtension.mappingServer.stubGetScheduledInstanceMappingWithError(SCHEDULE_INSTANCE_ID, 404)

      awsSnsClient.publishAttendanceDeleted(waitForTelemetry = "activity-attendance-delete-failed")

      await untilAsserted {
        assertThat(awsSqsActivityDlqClient.countMessagesOnQueue(activityDlqUrl).get()).isEqualTo(1)
      }
      NomisApiExtension.nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/schedules/$NOMIS_CRS_SCH_ID/booking/$NOMIS_BOOKING_ID/attendance")))
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-delete-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsScheduledInstanceId" to SCHEDULE_INSTANCE_ID.toString(),
              "bookingId" to NOMIS_BOOKING_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will send message to the DLQ when NOMIS API fails`() {
      MappingExtension.mappingServer.stubGetScheduleInstanceMapping(SCHEDULE_INSTANCE_ID, buildGetScheduleMappingResponse())
      NomisApiExtension.nomisApi.stubDeleteAttendanceWithError(NOMIS_CRS_SCH_ID, NOMIS_BOOKING_ID, 500)

      awsSnsClient.publishAttendanceDeleted(waitForTelemetry = "activity-attendance-delete-failed")

      await untilAsserted {
        assertThat(awsSqsActivityDlqClient.countMessagesOnQueue(activityDlqUrl).get()).isEqualTo(1)
      }
      verify(telemetryClient).trackEvent(
        eq("activity-attendance-delete-failed"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsScheduledInstanceId" to SCHEDULE_INSTANCE_ID.toString(),
              "bookingId" to NOMIS_BOOKING_ID.toString(),
              "nomisCourseScheduleId" to NOMIS_CRS_SCH_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will ignore a not found`() {
      MappingExtension.mappingServer.stubGetScheduleInstanceMapping(SCHEDULE_INSTANCE_ID, buildGetScheduleMappingResponse())
      NomisApiExtension.nomisApi.stubDeleteAttendanceWithError(NOMIS_CRS_SCH_ID, NOMIS_BOOKING_ID, 404)

      awsSnsClient.publishAttendanceDeleted(waitForTelemetry = "activity-attendance-delete-ignored")

      verify(telemetryClient).trackEvent(
        eq("activity-attendance-delete-ignored"),
        check {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "dpsScheduledInstanceId" to SCHEDULE_INSTANCE_ID.toString(),
              "bookingId" to NOMIS_BOOKING_ID.toString(),
              "nomisCourseScheduleId" to NOMIS_CRS_SCH_ID.toString(),
            ),
          )
        },
        isNull(),
      )
    }

    private fun SnsAsyncClient.publishAttendanceDeleted(waitForTelemetry: String = "activity-attendance-delete-success") = publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(attendanceDeletedMessagePayload("activities.prisoner.attendance-deleted", SCHEDULE_INSTANCE_ID, NOMIS_BOOKING_ID))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.prisoner.attendance-deleted").build(),
          ),
        ).build(),
    ).get()
      .also {
        waitForAnyProcessingToComplete(waitForTelemetry)
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

fun buildGetScheduleMappingResponse(
  nomisCourseScheduleId: Long = NOMIS_CRS_SCH_ID,
  scheduledInstanceId: Long = ACTIVITY_SCHEDULE_ID,
) = """{
          "nomisCourseScheduleId": $nomisCourseScheduleId,
          "scheduledInstanceId": $scheduledInstanceId,
          "mappingType": "ACTIVITY_CREATED"
        }
""".trimIndent()
