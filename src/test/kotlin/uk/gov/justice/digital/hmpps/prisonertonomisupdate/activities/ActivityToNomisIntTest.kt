package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

internal const val ACTIVITY_SCHEDULE_ID: Long = 100
internal const val ACTIVITY_ID: Long = 200
internal const val NOMIS_CRS_ACTY_ID: Long = 300
internal const val ALLOCATION_ID: Long = 400
internal const val NOMIS_BOOKING_ID: Long = 500
internal const val OFFENDER_PROGRAM_REFERENCE_ID: Long = 550
internal const val SCHEDULE_INSTANCE_ID: Long = 600
internal const val ATTENDANCE_ID: Long = 700
internal const val NOMIS_EVENT_ID: Long = 800
internal const val NOMIS_CRS_SCH_ID: Long = 900
internal const val OFFENDER_NO = "A1234AA"

class ActivityToNomisIntTest : SqsIntegrationTestBase() {

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Nested
  inner class CreateActivitySchedule {

    @Test
    fun `will consume a create activity schedule message`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingGivenActivityScheduleIdWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubCreateActivity()
      nomisApi.stubActivityCreate("""{ "courseActivityId": $NOMIS_CRS_ACTY_ID }""")

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { awsSqsActivityClient.countAllMessagesOnQueue(activityQueueUrl).get() } matches { it == 0 }
      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
      nomisApi.verify(
        postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("code", equalTo("$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-12")))
          .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("prisonId", equalTo("PVI")))
          .withRequestBody(matchingJsonPath("internalLocationId", equalTo("98877667")))
          .withRequestBody(matchingJsonPath("capacity", equalTo("10")))
          .withRequestBody(matchingJsonPath("payRates[0].incentiveLevel", equalTo("BAS")))
          .withRequestBody(matchingJsonPath("payRates[0].payBand", equalTo("1")))
          .withRequestBody(matchingJsonPath("payRates[0].rate", equalTo("1.5")))
          .withRequestBody(matchingJsonPath("description", equalTo("SAA Maths level 1")))
          .withRequestBody(matchingJsonPath("minimumIncentiveLevelCode", equalTo("BAS")))
          .withRequestBody(matchingJsonPath("programCode", equalTo("LEISURE_SOCIAL")))
          .withRequestBody(matchingJsonPath("payPerSession", equalTo("F")))
          .withRequestBody(matchingJsonPath("schedules[0].date", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("schedules[0].startTime", equalTo("09:00")))
          .withRequestBody(matchingJsonPath("schedules[0].endTime", equalTo("10:00")))
          .withRequestBody(matchingJsonPath("schedules[1].date", equalTo("2023-01-14")))
          .withRequestBody(matchingJsonPath("schedules[1].startTime", equalTo("14:00")))
          .withRequestBody(matchingJsonPath("schedules[1].endTime", equalTo("16:30")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].startTime", equalTo("07:45")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].endTime", equalTo("09:25")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].sunday", equalTo("true")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].tuesday", equalTo("false")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].wednesday", equalTo("false")))
          .withRequestBody(matchingJsonPath("scheduleRules[0].thursday", equalTo("true")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].startTime", equalTo("13:45")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].endTime", equalTo("14:25")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].sunday", equalTo("false")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].tuesday", equalTo("true")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].wednesday", equalTo("true")))
          .withRequestBody(matchingJsonPath("scheduleRules[1].thursday", equalTo("false")))
          .withRequestBody(matchingJsonPath("excludeBankHolidays", equalTo("false"))),
      )
      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/activities"))
          .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$NOMIS_CRS_ACTY_ID")))
          .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_CREATED"))),
      )
    }

    @Test
    fun `will retry after a mapping failure`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingGivenActivityScheduleIdWithError(ACTIVITY_SCHEDULE_ID, 404)
      nomisApi.stubActivityCreate("""{ "courseActivityId": $NOMIS_CRS_ACTY_ID }""")
      mappingServer.stubCreateActivityWithErrorFollowedBySuccess()

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
      await untilAsserted { verify(telemetryClient).trackEvent(eq("activity-create-mapping-retry"), any(), isNull()) }
      await untilAsserted {
        mappingServer.verify(
          exactly(2),
          postRequestedFor(urlEqualTo("/mapping/activities"))
            .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$NOMIS_CRS_ACTY_ID")))
            .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
            .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_CREATED"))),
        )
      }

      // no messages sent to DLQ
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches { it == 0 }
    }

    @Test
    fun `will log when duplicate is detected`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingGivenActivityScheduleIdWithError(ACTIVITY_SCHEDULE_ID, 404)
      nomisApi.stubActivityCreate("""{ "courseActivityId": $NOMIS_CRS_ACTY_ID }""")
      mappingServer.stubCreateActivityWithDuplicateError(
        activityScheduleId = ACTIVITY_SCHEDULE_ID,
        nomisCourseActivityId = NOMIS_CRS_ACTY_ID,
        duplicateNomisCourseActivityId = 301,
      )

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("activity-mapping-create-failed"),
          any(),
          isNull(),
        )
      }

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
      await untilAsserted { mappingServer.verify(exactly(1), postRequestedFor(urlEqualTo("/mapping/activities"))) }

      // no messages sent to DLQ
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches { it == 0 }

      verify(telemetryClient).trackEvent(
        eq("to-nomis-synch-activity-duplicate"),
        check {
          assertThat(it["existingActivityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["existingNomisCourseActivityId"]).isEqualTo("$NOMIS_CRS_ACTY_ID")
          assertThat(it["duplicateActivityScheduleId"]).isEqualTo("$ACTIVITY_SCHEDULE_ID")
          assertThat(it["duplicateNomisCourseActivityId"]).isEqualTo("301")
        },
        isNull(),
      )
    }

    @Test
    fun `constant mapping failure will result in DLQ message which can be retried`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingGivenActivityScheduleIdWithError(ACTIVITY_SCHEDULE_ID, 404)
      nomisApi.stubActivityCreate("""{ "courseActivityId": $NOMIS_CRS_ACTY_ID }""")
      mappingServer.stubCreateActivityWithError()

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }

      // the mapping call fails resulting in a retry message being queued
      // the retry message is processed and fails resulting in a message on the DLQ after 1 attempt
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches { it == 1 }

      // Next time the retry will succeed
      mappingServer.stubCreateActivity()

      webTestClient.put()
        .uri("/queue-admin/retry-all-dlqs")
        .exchange()
        .expectStatus()
        .isOk

      await untilCallTo { mappingServer.postCountFor("/mapping/activities") } matches { it == 3 } // 1 initial call, 1 retry and 1 final successful call
      await untilCallTo { awsSqsActivityClient.countAllMessagesOnQueue(activityQueueUrl).get() } matches { it == 0 }
      await untilCallTo {
        awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()
      } matches {
        log.trace("Messages on queue: {}", it)
        it == 0
      }
    }
  }

  @Nested
  inner class UpdateActivitySchedule {
    @Test
    fun `should update an activity`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildGetScheduleResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildGetActivityResponse())
      mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      nomisApi.stubActivityUpdate(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(amendActivityEvent()).get()

      await untilAsserted { activitiesApi.verify(getRequestedFor(urlEqualTo("/schedules/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted { activitiesApi.verify(getRequestedFor(urlEqualTo("/activities/$ACTIVITY_ID"))) }
      await untilAsserted { mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted {
        nomisApi.verify(
          putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID"))
            .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-23")))
            .withRequestBody(matchingJsonPath("internalLocationId", equalTo("98877667")))
            .withRequestBody(matchingJsonPath("payRates[0].incentiveLevel", equalTo("BAS")))
            .withRequestBody(matchingJsonPath("payRates[0].payBand", equalTo("1")))
            .withRequestBody(matchingJsonPath("payRates[0].rate", equalTo("1.5")))
            .withRequestBody(matchingJsonPath("scheduleRules[0].startTime", equalTo("07:45")))
            .withRequestBody(matchingJsonPath("scheduleRules[0].endTime", equalTo("09:25")))
            .withRequestBody(matchingJsonPath("scheduleRules[0].tuesday", equalTo("false")))
            .withRequestBody(matchingJsonPath("scheduleRules[0].thursday", equalTo("true")))
            .withRequestBody(matchingJsonPath("scheduleRules[1].startTime", equalTo("13:45")))
            .withRequestBody(matchingJsonPath("scheduleRules[1].endTime", equalTo("14:25")))
            .withRequestBody(matchingJsonPath("scheduleRules[1].tuesday", equalTo("true")))
            .withRequestBody(matchingJsonPath("scheduleRules[1].thursday", equalTo("false"))),
        )
      }
      assertThat(awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get()).isEqualTo(0)
    }

    @Test
    fun `should put message on DLQ if any external API fails`() {
      activitiesApi.stubGetScheduleWithError(ACTIVITY_SCHEDULE_ID)

      awsSnsClient.publish(amendActivityEvent()).get()

      await untilAsserted {
        assertThat(
          awsSqsActivityDlqClient.countAllMessagesOnQueue(activityDlqUrl).get(),
        ).isEqualTo(1)
      }
      nomisApi.verify(exactly(0), putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID")))
    }

    private fun amendActivityEvent(): PublishRequest? =
      PublishRequest.builder().topicArn(topicArn)
        .message(activityMessagePayload("activities.activity-schedule.amended", ACTIVITY_SCHEDULE_ID))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.activity-schedule.amended").build(),
          ),
        ).build()
  }

  @Nested
  inner class DeleteAll {
    @Test
    fun `should delete all activities`() {
      mappingServer.stubGetAllActivityMappings(
        """[
           { "activityScheduleId": 101, "nomisCourseActivityId": 201, "mappingType": "ACTIVITY_CREATED", "whenCreated": "2020-01-01T00:00:00Z" },
           { "activityScheduleId": 102, "nomisCourseActivityId": 202, "mappingType": "MIGRATED" }
           ]
          """.trimIndent(),
      )
      mappingServer.stubDeleteActivityMapping(101)
      mappingServer.stubDeleteActivityMapping(102)
      nomisApi.stubActivityDelete(201)
      nomisApi.stubActivityDelete(202)

      webTestClient.delete()
        .uri("/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_QUEUE_ADMIN")))
        .exchange()
        .expectStatus()
        .isNoContent

      await untilAsserted {
        mappingServer.verify(deleteRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/101")))
        mappingServer.verify(deleteRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/102")))
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/activities/201")))
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/activities/202")))
      }
    }
  }
}

fun buildGetActivityResponse(id: Long = ACTIVITY_ID): String =
  """
    {
  "id": $id,
  "prisonCode": "PVI",
  "attendanceRequired": false,
  "inCell": false,
  "pieceWork": false,
  "outsideWork": false,
  "payPerSession": "F",
  "summary": "Maths level 1",
  "description": "A basic maths course suitable for introduction to the subject",
  "category": {
    "id": 1,
    "code": "LEISURE_SOCIAL",
    "name": "Leisure and social",
    "description": "Such as association, library time and social clubs, like music or art"
  },
  "eligibilityRules": [],
  "schedules": [],
  "waitingList": [],
  "pay": [
    {
      "id": 3579,
      "incentiveLevel": "Basic",
      "incentiveNomisCode": "BAS",
      "prisonPayBand": {
         "id": 987,
         "displaySequence": 1,
         "alias": "Low",
         "description": "Pay band 1",
         "nomisPayBand": 1,
         "prisonCode": "PVI"
      },
      "rate": 150,
      "pieceRate": 250,
      "pieceRateItems": 10
    }
  ],
  "startDate": "2023-01-12",
  "endDate": "2023-01-13",
  "riskLevel": "High",
  "minimumIncentiveLevel": "Basic",
  "minimumIncentiveNomisCode": "BAS",
  "createdTime": "2023-01-12T17:26:18.332Z",
  "createdBy": "Adam Smith",
  "minimumEducationLevel": [
    {
      "id": 123456,
      "educationLevelCode": "Basic",
      "educationLevelDescription": "Basic"
    }
  ]
}
  """.trimIndent()

fun buildGetScheduleResponse(id: Long = ACTIVITY_SCHEDULE_ID): String =
  """
{
  "id": $id,
  "instances": [
    {
      "id": 3456,
      "date": "2023-01-13",
      "startTime": "09:00",
      "endTime": "10:00",
      "cancelled": false,
      "cancelledTime": "2023-01-13T09:38:26.092Z",
      "cancelledBy": "Adam Smith",
      "attendances": []
    },
    {
      "id": 3457,
      "date": "2023-01-14",
      "startTime": "14:00",
      "endTime": "16:30",
      "cancelled": false,
      "cancelledTime": "2023-01-13T09:38:26.092Z",
      "cancelledBy": "Adam Smith",
      "attendances": []
    }
  ],
  "allocations": [],
  "description": "Monday AM Houseblock 3",
  "suspensions": [
    {
      "suspendedFrom": "2023-01-13",
      "suspendedUntil": "2023-01-13"
    }
  ],
  "internalLocation": {
    "id": 98877667,
    "code": "EDU-ROOM-1",
    "description": "Education - R1"
  },
  "capacity": 10,
  "activity": {
    "id": $ACTIVITY_ID,
    "prisonCode": "PVI",
    "attendanceRequired": false,
    "inCell": false,
    "pieceWork": false,
    "outsideWork": false,
    "payPerSession": "F",
    "summary": "Maths level 1",
    "description": "A basic maths course suitable for introduction to the subject",
    "category": {
      "id": 1,
      "code": "LEISURE_SOCIAL",
      "name": "Leisure and social",
      "description": "Such as association, library time and social clubs, like music or art"
    },
    "riskLevel": "High",
    "minimumIncentiveLevel": "Basic",
    "minimumIncentiveNomisCode": "BAS",
    "minimumEducationLevel": [
      {
        "id": 123456,
        "educationLevelCode": "Basic",
        "educationLevelDescription": "Basic"
      }
    ]
  },
  "slots": [{
    "id"        : 555666001,
    "startTime" : "07:45",
    "endTime"   : "09:25",
    "daysOfWeek": ["Sun","Thu"],
    "mondayFlag": false,
    "tuesdayFlag": false,
    "wednesdayFlag": false,
    "thursdayFlag": true,
    "fridayFlag": false,
    "saturdayFlag": false,
    "sundayFlag": true
  },
  {
    "id"        : 555666002,
    "startTime" : "13:45",
    "endTime"   : "14:25",
    "daysOfWeek": ["Tue","Wed"],
    "mondayFlag": false,
    "tuesdayFlag": true,
    "wednesdayFlag": true,
    "thursdayFlag": false,
    "fridayFlag": false,
    "saturdayFlag": false,
    "sundayFlag": false
  }],
  "startDate" : "2023-01-20",
  "endDate" : "2023-01-23",
  "runsOnBankHoliday": true
}
  """.trimIndent()

fun buildGetMappingResponse(
  nomisActivityId: Long = NOMIS_CRS_ACTY_ID,
  activityScheduleId: Long = ACTIVITY_SCHEDULE_ID,
) =
  """{
          "nomisCourseActivityId": $nomisActivityId,
          "activityScheduleId": $activityScheduleId,
          "mappingType": "TYPE"
        }
  """.trimIndent()
