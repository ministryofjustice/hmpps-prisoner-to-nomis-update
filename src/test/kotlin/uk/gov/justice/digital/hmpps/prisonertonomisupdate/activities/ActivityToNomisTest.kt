package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.allocationMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val activityScheduleId: Long = 100
private const val activityId: Long = 200
private const val courseActivityId: Long = 300
private const val allocationId: Long = 400
private const val bookingId: Long = 500

class ActivityToNomisTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a create activity schedule message`() {
    activitiesApi.stubGetSchedule(activityScheduleId, buildApiActivityScheduleDtoJsonResponse())
    activitiesApi.stubGetActivity(activityId, buildApiActivityDtoJsonResponse())
    mappingServer.stubGetMappingGivenActivityScheduleIdWithError(activityScheduleId, 404)
    mappingServer.stubCreateActivity()
    nomisApi.stubActivityCreate("""{ "courseActivityId": $courseActivityId }""")

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(activityMessagePayload("activities.activity-schedule.created", activityScheduleId))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.activity-schedule.created").build(),
          )
        ).build()
    ).get()

    await untilCallTo { awsSqsActivityClient.countAllMessagesOnQueue(activityQueueUrl).get() } matches { it == 0 }
    await untilCallTo { activitiesApi.getCountFor("/activities/$activityId") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/activities"))
        .withRequestBody(WireMock.matchingJsonPath("capacity", WireMock.equalTo("10")))
        .withRequestBody(WireMock.matchingJsonPath("startDate", WireMock.equalTo("2023-01-12")))
        .withRequestBody(WireMock.matchingJsonPath("prisonId", WireMock.equalTo("PVI")))
    )
    mappingServer.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/activities"))
        .withRequestBody(WireMock.matchingJsonPath("nomisCourseActivityId", WireMock.equalTo("$courseActivityId")))
        .withRequestBody(WireMock.matchingJsonPath("activityScheduleId", WireMock.equalTo("$activityScheduleId")))
        .withRequestBody(WireMock.matchingJsonPath("mappingType", WireMock.equalTo("ACTIVITY_CREATED")))
    )
  }

  @Test
  fun `will retry after a mapping failure`() {
    activitiesApi.stubGetSchedule(activityScheduleId, buildApiActivityScheduleDtoJsonResponse())
    activitiesApi.stubGetActivity(activityId, buildApiActivityDtoJsonResponse())
    mappingServer.stubGetMappingGivenActivityScheduleIdWithError(activityScheduleId, 404)
    nomisApi.stubActivityCreate("""{ "courseActivityId": $courseActivityId }""")
    mappingServer.stubCreateActivityWithError()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(activityMessagePayload("activities.activity-schedule.created", activityScheduleId))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.activity-schedule.created").build(),
          )
        ).build()
    ).get()

    await untilCallTo { activitiesApi.getCountFor("/activities/$activityId") } matches { it == 1 }
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

  @Test
  fun `will consume an allocation message`() {
    activitiesApi.stubGetAllocation(allocationId, buildApiAllocationDtoJsonResponse())
    mappingServer.stubGetMappingGivenActivityScheduleId(
      activityScheduleId,
      """{
          "nomisCourseActivityId": $courseActivityId,
          "activityScheduleId": $activityScheduleId,
          "mappingType": "TYPE"
        }
      """.trimIndent(),
    )
    nomisApi.stubAllocationCreate(courseActivityId)

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(allocationMessagePayload("activities.prisoner.allocated", activityScheduleId, allocationId))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.prisoner.allocated").build(),
          )
        ).build()
    ).get()

    await untilCallTo { activitiesApi.getCountFor("/allocations/$allocationId") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/activities/$courseActivityId") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/activities/$courseActivityId"))
        .withRequestBody(WireMock.matchingJsonPath("bookingId", WireMock.equalTo("$bookingId")))
        .withRequestBody(WireMock.matchingJsonPath("startDate", WireMock.equalTo("2023-01-12")))
        .withRequestBody(WireMock.matchingJsonPath("endDate", WireMock.equalTo("2023-01-13")))
        .withRequestBody(WireMock.matchingJsonPath("payBandCode", WireMock.equalTo("7")))
    )
  }

  fun buildApiActivityScheduleDtoJsonResponse(id: Long = activityScheduleId): String =
    """
{
  "id": $id,
  "instances": [
    {
      "id": 3456,
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
    "id": $activityId,
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

  fun buildApiActivityDtoJsonResponse(id: Long = activityId): String =
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

  fun buildApiAllocationDtoJsonResponse(id: Long = allocationId): String {
    return """
  {
    "id": $id,
    "prisonerNumber": "A1234AA",
    "bookingId": $bookingId,
    "startDate": "2023-01-12",
    "endDate": "2023-01-13",
    "payBand": "7",
    "scheduleDescription" : "description",
    "activitySummary" : "summary"
  }
      """
  }
}
