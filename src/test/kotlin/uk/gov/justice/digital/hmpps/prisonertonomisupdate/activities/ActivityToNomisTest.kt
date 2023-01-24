package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class ActivityToNomisTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a create message`() {
    activitiesApi.stubGetSchedule(12, buildApiActivityScheduleDtoJsonResponse())
    activitiesApi.stubGetActivity(123456, buildApiActivityDtoJsonResponse())
    mappingServer.stubGetMappingGivenActivityScheduleIdWithError(12, 404)
    mappingServer.stubCreateActivity()
    nomisApi.stubActivityCreate()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(activityMessagePayload("activities.activity-schedule.created", 12))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.activity-schedule.created").build(),
          )
        ).build()
    ).get()

    await untilCallTo { awsSqsActivityClient.countAllMessagesOnQueue(activityQueueUrl).get() } matches { it == 0 }
    await untilCallTo { activitiesApi.getCountFor("/activities/123456") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/activities"))
        .withRequestBody(WireMock.matchingJsonPath("capacity", WireMock.equalTo("10")))
        .withRequestBody(WireMock.matchingJsonPath("startDate", WireMock.equalTo("2023-01-12")))
        .withRequestBody(WireMock.matchingJsonPath("prisonId", WireMock.equalTo("PVI")))
    )
    mappingServer.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/activities"))
        .withRequestBody(WireMock.matchingJsonPath("nomisCourseActivityId", WireMock.equalTo("456")))
        .withRequestBody(WireMock.matchingJsonPath("activityScheduleId", WireMock.equalTo("12")))
        .withRequestBody(WireMock.matchingJsonPath("mappingType", WireMock.equalTo("ACTIVITY_CREATED")))
    )
  }

  @Test
  fun `will retry after a mapping failure`() {
    activitiesApi.stubGetSchedule(12, buildApiActivityScheduleDtoJsonResponse())
    activitiesApi.stubGetActivity(123456, buildApiActivityDtoJsonResponse())
    mappingServer.stubGetMappingGivenActivityScheduleIdWithError(12, 404)
    nomisApi.stubActivityCreate()
    mappingServer.stubCreateActivityWithError()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(activityMessagePayload("activities.activity-schedule.created", 12))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.activity-schedule.created").build(),
          )
        ).build()
    ).get()

    await untilCallTo { activitiesApi.getCountFor("/activities/123456") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }

    // the mapping call fails resulting in a retry message being queued
    // the retry message is processed and fails resulting in a message on the DLQ after 1 attempt
    await untilCallTo { awsSqsActivityDlqClient!!.countAllMessagesOnQueue(activityDlqUrl!!).get() } matches { it == 1 }

    // Next time the retry will succeed
    mappingServer.stubCreateActivity()

    webTestClient.put()
      .uri("/queue-admin/retry-all-dlqs")
      .exchange()
      .expectStatus()
      .isOk

    await untilCallTo { mappingServer.postCountFor("/mapping/activities") } matches { it == 3 } // 1 initial call, 1 retry and 1 final successful call
    await untilCallTo { awsSqsActivityClient.countAllMessagesOnQueue(activityQueueUrl).get() } matches { it == 0 }
    await untilCallTo { awsSqsActivityDlqClient!!.countAllMessagesOnQueue(activityDlqUrl!!).get() } matches { it == 0 }
  }

  fun buildApiActivityScheduleDtoJsonResponse(id: Long = 12): String =
    """
{
  "id": $id,
  "instances": [
    {
      "id": 123456,
      "date": "2023-01-13",
      "startTime": "9:00",
      "endTime": "10:00",
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
    "id": 123456,
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
    "minimumIncentiveLevel": "Basic"
  },
  "slots": [],
  "startDate" : "2023-01-20"
}
    """.trimIndent()

  fun buildApiActivityDtoJsonResponse(id: Long = 123456): String =
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
      "id": 123456,
      "incentiveLevel": "Basic",
      "payBand": "A",
      "rate": 150,
      "pieceRate": 150,
      "pieceRateItems": 10
    }
  ],
  "startDate": "2023-01-12",
  "endDate": "2023-01-12",
  "riskLevel": "High",
  "minimumIncentiveLevel": "Basic",
  "createdTime": "2023-01-12T17:26:18.332Z",
  "createdBy": "Adam Smith"
}
    """
}
