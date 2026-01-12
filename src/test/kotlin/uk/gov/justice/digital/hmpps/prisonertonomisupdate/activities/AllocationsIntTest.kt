package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.allocationMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

class AllocationsIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class AllocatePrisoner {
    @Test
    fun `will consume an allocation message`() {
      ActivitiesApiExtension.activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationDtoJsonResponse())
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubAllocationUpsert(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.allocated", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.allocated").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/allocations/id/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/activities/$NOMIS_CRS_ACTY_ID/allocation") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocation"))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("$NOMIS_BOOKING_ID")))
          .withRequestBody(matchingJsonPath("payBandCode", equalTo("7")))
          .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-12")))
          .withRequestBody(matchingJsonPath("programStatusCode", equalTo("ALLOC"))),
      )
    }

    @Test
    fun `will consume an allocation message with a missing pay band`() {
      ActivitiesApiExtension.activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationDtoWithMissingPayBand())
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubAllocationUpsert(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.allocated", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.allocated").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/allocations/id/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/activities/$NOMIS_CRS_ACTY_ID/allocation") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocation"))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("$NOMIS_BOOKING_ID")))
          .withRequestBody(matchingJsonPath("payBandCode", absent())),
      )
    }
  }

  @Nested
  inner class DeallocatePrisoner {
    @Test
    fun `will consume an allocation amended message`() {
      ActivitiesApiExtension.activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationDeallocatedJsonResponse())
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubAllocationUpsert(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.allocation-amended", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.allocation-amended").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/allocations/id/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/activities/$NOMIS_CRS_ACTY_ID/allocation") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocation"))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("$NOMIS_BOOKING_ID")))
          .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-12")))
          .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("payBandCode", equalTo("7")))
          .withRequestBody(matchingJsonPath("endReason", equalTo("OTH")))
          .withRequestBody(matchingJsonPath("endComment", equalTo("Deallocated in DPS by ANOTHER_USER at 2023-01-13 18:49:04 for reason Released from prison")))
          .withRequestBody(matchingJsonPath("suspended", equalTo("false")))
          .withRequestBody(matchingJsonPath("programStatusCode", equalTo("END"))),
      )
    }

    @Test
    fun `will build deallocated message from planned deallocation if available`() {
      ActivitiesApiExtension.activitiesApi.stubGetAllocation(
        ALLOCATION_ID,
        buildApiAllocationDeallocatedJsonResponse(
          plannedDeallocation = """
            "plannedDeallocation": {
              "id": 1234,
              "plannedDate": "2023-01-10",
              "plannedBy": "Planned by user",
              "plannedReason": {
                "code": "PLR",
                "description": "Planned reason description"
              },
              "plannedAt": "2023-01-10T12:34:56.789Z"
            },
          """.trimIndent(),
        ),
      )
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubAllocationUpsert(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.allocation-amended", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.allocation-amended").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/allocations/id/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/activities/$NOMIS_CRS_ACTY_ID/allocation") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocation"))
          .withRequestBody(matchingJsonPath("endComment", equalTo("Deallocated in DPS by Planned by user at 2023-01-10 12:34:56 for reason Planned reason description"))),
      )
    }
  }

  @Nested
  inner class SuspendPrisoner {
    @Test
    fun `will consume an allocation amended message`() {
      ActivitiesApiExtension.activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationSuspendedJsonResponse())
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubAllocationUpsert(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.allocation-amended", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.allocation-amended").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/allocations/id/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/activities/$NOMIS_CRS_ACTY_ID/allocation") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocation"))
          .withRequestBody(matchingJsonPath("suspended", equalTo("true")))
          .withRequestBody(matchingJsonPath("suspendedComment", equalTo("SUSPENDED in DPS by SUSPEND_USER at 2023-01-13 18:49:04 for reason HOSPITAL")))
          .withRequestBody(matchingJsonPath("programStatusCode", equalTo("ALLOC"))),
      )
    }
  }

  @Nested
  inner class Exclusions {
    @Test
    fun `will include exclusions when creating an allocation`() {
      ActivitiesApiExtension.activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationWithExclusionsJsonResponse())
      MappingExtension.mappingServer.stubGetMappings(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubAllocationUpsert(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.allocated", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.allocated").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/allocations/id/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/activities/$NOMIS_CRS_ACTY_ID/allocation") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocation"))
          .withRequestBody(matchingJsonPath("exclusions[*][?(@.day == 'MON' && @.slot == 'AM')]"))
          .withRequestBody(matchingJsonPath("exclusions[*][?(@.day == 'TUE' && @.slot == 'AM')]"))
          .withRequestBody(matchingJsonPath("exclusions[*][?(@.day == 'FRI' && @.slot == null)]")),
      )
    }
  }
}

fun buildApiAllocationDtoJsonResponse(id: Long = ALLOCATION_ID): String = """
  {
    "id": $id,
    "activityId": 1234,
    "isUnemployment": false,
    "prisonerNumber": "A1234AA",
    "bookingId": $NOMIS_BOOKING_ID,
    "activityId": 1234,
    "scheduleId": $ACTIVITY_SCHEDULE_ID,
    "startDate": "2023-01-12",
    "prisonPayBand": {
      "id": 1,
      "displaySequence": 1,
      "alias": "seven",
      "description": "seven",
      "nomisPayBand": 7,
      "prisonCode": "MDI"
    },
    "allocatedBy": "SOME_USER",
    "allocatedTime": "2023-01-10T14:46:05.849Z",
    "scheduleDescription" : "description",
    "activitySummary" : "summary",
    "status": "ACTIVE",
    "exclusions": [],
    "isUnemployment": false
  }
""".trimIndent()

fun buildApiAllocationDtoWithMissingPayBand(id: Long = ALLOCATION_ID): String = """
  {
    "id": $id,
    "activityId": 1234,
    "isUnemployment": false,
    "prisonerNumber": "A1234AA",
    "bookingId": $NOMIS_BOOKING_ID,
    "scheduleId": $ACTIVITY_SCHEDULE_ID,
    "startDate": "2023-01-12",
    "allocatedBy": "SOME_USER",
    "allocatedTime": "2023-01-10T14:46:05.849Z",
    "scheduleDescription" : "description",
    "activitySummary" : "summary",
    "status": "ACTIVE",
    "exclusions": []
  }
""".trimIndent()

fun buildApiAllocationDeallocatedJsonResponse(id: Long = ALLOCATION_ID, plannedDeallocation: String = ""): String = """
  {
    "id": $id,
    "activityId": 1234,
    "isUnemployment": false,
    "prisonerNumber": "A1234AA",
    "bookingId": $NOMIS_BOOKING_ID,
    "scheduleId": $ACTIVITY_SCHEDULE_ID,
    "startDate": "2023-01-12",
    "endDate": "2023-01-13",
    "prisonPayBand": {
      "id": 1,
      "displaySequence": 1,
      "alias": "seven",
      "description": "seven",
      "nomisPayBand": 7,
      "prisonCode": "MDI"
    },
    "allocatedBy": "SOME_USER",
    "allocatedTime": "2023-01-10T14:46:05.849Z",
    "deallocatedBy": "ANOTHER_USER",
    "deallocatedReason": {
      "code": "RELEASED",
      "description": "Released from prison"
    },
    "deallocatedTime": "2023-01-13T18:49:04.837Z",
    $plannedDeallocation
    "scheduleDescription" : "description",
    "activitySummary" : "summary",
    "status": "ENDED",
    "exclusions": []
  }
""".trimIndent()

fun buildApiAllocationSuspendedJsonResponse(id: Long = ALLOCATION_ID): String = """
  {
    "id": $id,
    "activityId": 1234,
    "isUnemployment": false,
    "prisonerNumber": "A1234AA",
    "bookingId": $NOMIS_BOOKING_ID,
    "scheduleId": $ACTIVITY_SCHEDULE_ID,
    "startDate": "2023-01-12",
    "prisonPayBand": {
      "id": 1,
      "displaySequence": 1,
      "alias": "seven",
      "description": "seven",
      "nomisPayBand": 7,
      "prisonCode": "MDI"
    },
    "allocatedBy": "SOME_USER",
    "allocatedTime": "2023-01-10T14:46:05.849Z",
    "suspendedBy": "SUSPEND_USER",
    "suspendedReason": "HOSPITAL",
    "suspendedTime": "2023-01-13T18:49:04.837Z",
    "scheduleDescription" : "description",
    "activitySummary" : "summary",
    "status": "SUSPENDED",
    "exclusions": []
  }
""".trimIndent()

fun buildApiAllocationWithExclusionsJsonResponse(id: Long = ALLOCATION_ID): String = """
  {
    "id": $id,
    "activityId": 1234,
    "isUnemployment": false,
    "prisonerNumber": "A1234AA",
    "bookingId": $NOMIS_BOOKING_ID,
    "scheduleId": $ACTIVITY_SCHEDULE_ID,
    "startDate": "2023-01-12",
    "prisonPayBand": {
      "id": 1,
      "displaySequence": 1,
      "alias": "seven",
      "description": "seven",
      "nomisPayBand": 7,
      "prisonCode": "MDI"
    },
    "allocatedBy": "SOME_USER",
    "allocatedTime": "2023-01-10T14:46:05.849Z",
    "scheduleDescription" : "description",
    "activitySummary" : "summary",
    "status": "ACTIVE",
    "exclusions": [
      {
        "weekNumber": 1,
        "timeSlot": "AM",
        "monday": true,
        "tuesday": true,
        "wednesday": false,
        "thursday": false,
        "friday": true,
        "saturday": false,
        "sunday": false,
        "daysOfWeek": [
          "MONDAY",
          "TUESDAY",
          "FRIDAY"
        ]
      },
      {
        "weekNumber": 1,
        "timeSlot": "PM",
        "monday": false,
        "tuesday": false,
        "wednesday": false,
        "thursday": false,
        "friday": true,
        "saturday": false,
        "sunday": false,
        "daysOfWeek": [
          "FRIDAY"
        ]
      },
      {
        "weekNumber": 1,
        "timeSlot": "ED",
        "monday": false,
        "tuesday": false,
        "wednesday": false,
        "thursday": false,
        "friday": true,
        "saturday": false,
        "sunday": false,
        "daysOfWeek": [
          "FRIDAY"
        ]
      }
    ]
  }
""".trimIndent()
