package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
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
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.activityMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.allocationMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val ACTIVITY_SCHEDULE_ID: Long = 100
private const val ACTIVITY_ID: Long = 200
private const val COURSE_ACTIVITY_ID: Long = 300
private const val ALLOCATION_ID: Long = 400
private const val BOOKING_ID: Long = 500

class ActivityToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateActivitySchedule {

    @Test
    fun `will consume a create activity schedule message`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildApiActivityScheduleDtoJsonResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildApiActivityDtoJsonResponse())
      mappingServer.stubGetMappingGivenActivityScheduleIdWithError(ACTIVITY_SCHEDULE_ID, 404)
      mappingServer.stubCreateActivity()
      nomisApi.stubActivityCreate("""{ "courseActivityId": $COURSE_ACTIVITY_ID }""")

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            )
          ).build()
      ).get()

      await untilCallTo { awsSqsActivityClient.countAllMessagesOnQueue(activityQueueUrl).get() } matches { it == 0 }
      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }
      nomisApi.verify(
        WireMock.postRequestedFor(urlEqualTo("/activities"))
          .withRequestBody(matchingJsonPath("code", equalTo("$ACTIVITY_ID-$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-12")))
          .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("prisonId", equalTo("PVI")))
          .withRequestBody(matchingJsonPath("internalLocationId", equalTo("98877667")))
          .withRequestBody(matchingJsonPath("capacity", equalTo("10")))
          .withRequestBody(matchingJsonPath("payRates[0].incentiveLevel", equalTo("BAS")))
          .withRequestBody(matchingJsonPath("payRates[0].payBand", equalTo("1")))
          .withRequestBody(matchingJsonPath("payRates[0].rate", equalTo("1.5")))
          .withRequestBody(matchingJsonPath("description", equalTo("A basic maths course suitable for introduction to the subject - Monday AM Houseblock 3")))
          .withRequestBody(matchingJsonPath("minimumIncentiveLevelCode", equalTo("Basic")))
          .withRequestBody(matchingJsonPath("programCode", equalTo("LEISURE_SOCIAL")))
          .withRequestBody(matchingJsonPath("payPerSession", equalTo("F")))
      )
      mappingServer.verify(
        WireMock.postRequestedFor(urlEqualTo("/mapping/activities"))
          .withRequestBody(matchingJsonPath("nomisCourseActivityId", equalTo("$COURSE_ACTIVITY_ID")))
          .withRequestBody(matchingJsonPath("activityScheduleId", equalTo("$ACTIVITY_SCHEDULE_ID")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("ACTIVITY_CREATED")))
      )
    }

    @Test
    fun `will retry after a mapping failure`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildApiActivityScheduleDtoJsonResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildApiActivityDtoJsonResponse())
      mappingServer.stubGetMappingGivenActivityScheduleIdWithError(ACTIVITY_SCHEDULE_ID, 404)
      nomisApi.stubActivityCreate("""{ "courseActivityId": $COURSE_ACTIVITY_ID }""")
      mappingServer.stubCreateActivityWithError()

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(activityMessagePayload("activities.activity-schedule.created", ACTIVITY_SCHEDULE_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.activity-schedule.created").build(),
            )
          ).build()
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/activities/$ACTIVITY_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities") } matches { it == 1 }

      // the mapping call fails resulting in a retry message being queued
      // the retry message is processed and fails resulting in a message on the DLQ after 1 attempt
      await untilCallTo {
        awsSqsActivityDlqClient!!.countAllMessagesOnQueue(activityDlqUrl!!).get()
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
        awsSqsActivityDlqClient!!.countAllMessagesOnQueue(activityDlqUrl!!).get()
      } matches { it == 0 }
    }
  }

  @Nested
  inner class UpdateActivitySchedule {
    @Test
    fun `should update an activity`() {
      activitiesApi.stubGetSchedule(ACTIVITY_SCHEDULE_ID, buildApiActivityScheduleDtoJsonResponse())
      activitiesApi.stubGetActivity(ACTIVITY_ID, buildApiActivityDtoJsonResponse())
      mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildMappingDtoResponse())
      nomisApi.stubActivityUpdate(COURSE_ACTIVITY_ID)

      awsSnsClient.publish(amendActivityEvent()).get()

      await untilAsserted { activitiesApi.verify(getRequestedFor(urlEqualTo("/schedules/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted { activitiesApi.verify(getRequestedFor(urlEqualTo("/activities/$ACTIVITY_ID"))) }
      await untilAsserted { mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/$ACTIVITY_SCHEDULE_ID"))) }
      await untilAsserted {
        nomisApi.verify(
          putRequestedFor(urlEqualTo("/activities/$COURSE_ACTIVITY_ID"))
            .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-23")))
            .withRequestBody(matchingJsonPath("internalLocationId", equalTo("98877667")))
            .withRequestBody(matchingJsonPath("payRates[0].incentiveLevel", equalTo("BAS")))
            .withRequestBody(matchingJsonPath("payRates[0].payBand", equalTo("1")))
            .withRequestBody(matchingJsonPath("payRates[0].rate", equalTo("1.5")))
        )
      }
      assertThat(awsSqsActivityDlqClient!!.countAllMessagesOnQueue(activityDlqUrl!!).get()).isEqualTo(0)
    }

    @Test
    fun `should put message on DLQ if any external API fails`() {
      activitiesApi.stubGetScheduleWithError(ACTIVITY_SCHEDULE_ID)

      awsSnsClient.publish(amendActivityEvent()).get()

      await untilAsserted { assertThat(awsSqsActivityDlqClient!!.countAllMessagesOnQueue(activityDlqUrl!!).get()).isEqualTo(1) }
      nomisApi.verify(exactly(0), putRequestedFor(urlEqualTo("/activities/$COURSE_ACTIVITY_ID")))
    }

    private fun amendActivityEvent(): PublishRequest? =
      PublishRequest.builder().topicArn(topicArn)
        .message(activityMessagePayload("activities.activity-schedule.amended", ACTIVITY_SCHEDULE_ID))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("activities.activity-schedule.amended").build(),
          )
        ).build()
  }

  @Nested
  inner class AllocatePrisoner {
    @Test
    fun `will consume an allocation message`() {
      activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationDtoJsonResponse())
      mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildMappingDtoResponse())
      nomisApi.stubAllocationCreate(COURSE_ACTIVITY_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.allocated", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.allocated").build(),
            )
          ).build()
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/allocations/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/activities/$COURSE_ACTIVITY_ID") } matches { it == 1 }
      nomisApi.verify(
        WireMock.postRequestedFor(urlEqualTo("/activities/$COURSE_ACTIVITY_ID"))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("$BOOKING_ID")))
          .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-12")))
          .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("payBandCode", equalTo("7")))
      )
    }
  }

  @Nested
  inner class DeallocatePrisoner {
    @Test
    fun `will consume a deallocation message`() {
      activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationDtoJsonResponse())
      mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildMappingDtoResponse())
      nomisApi.stubDeallocate(COURSE_ACTIVITY_ID, BOOKING_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.deallocated", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.deallocated").build(),
            )
          ).build()
      ).get()

      await untilCallTo { activitiesApi.getCountFor("/allocations/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.putCountFor("/activities/$COURSE_ACTIVITY_ID/booking-id/$BOOKING_ID/end") } matches { it == 1 }
      nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$COURSE_ACTIVITY_ID/booking-id/$BOOKING_ID/end"))
          .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("endReason", equalTo("END")))
      )
    }
  }

  fun buildApiActivityScheduleDtoJsonResponse(id: Long = ACTIVITY_SCHEDULE_ID): String =
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
    "minimumIncentiveLevel": "BAS"
  },
  "slots": [],
  "startDate" : "2023-01-20",
  "endDate" : "2023-01-23"
}
    """.trimIndent()

  fun buildApiActivityDtoJsonResponse(id: Long = ACTIVITY_ID): String =
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
      "incentiveLevel": "BAS",
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
  "createdTime": "2023-01-12T17:26:18.332Z",
  "createdBy": "Adam Smith"
}
   """

  fun buildMappingDtoResponse(nomisActivityId: Long = COURSE_ACTIVITY_ID, activityScheduleId: Long = ACTIVITY_SCHEDULE_ID) =
    """{
          "nomisCourseActivityId": $nomisActivityId,
          "activityScheduleId": $activityScheduleId,
          "mappingType": "TYPE"
        }
    """.trimIndent()

  fun buildApiAllocationDtoJsonResponse(id: Long = ALLOCATION_ID): String {
    return """
  {
    "id": $id,
    "prisonerNumber": "A1234AA",
    "bookingId": $BOOKING_ID,
    "startDate": "2023-01-12",
    "endDate": "2023-01-13",
    "payBandId": 7,
    "deallocatedReason": "END",
    "scheduleDescription" : "description",
    "activitySummary" : "summary"
  }
      """
  }
}