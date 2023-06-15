package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

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
          .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-12"))),
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
          .withRequestBody(matchingJsonPath("suspended", equalTo("false"))),
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
          .withRequestBody(matchingJsonPath("suspendedComment", equalTo("Suspended in DPS by SUSPEND_USER at 2023-01-13 18:49:04 for reason HOSPITAL"))),
      )
    }
  }
}

fun buildApiAllocationDtoJsonResponse(id: Long = ALLOCATION_ID): String {
  return """
  {
    "id": $id,
    "prisonerNumber": "A1234AA",
    "bookingId": $NOMIS_BOOKING_ID,
    "scheduleId": $ACTIVITY_SCHEDULE_ID,
    "startDate": "2023-01-12",
    "payRate": {
      "id": 123,
      "incentiveLevel": "1",
      "incentiveNomisCode": "BAS",
      "prisonPayBand": {
        "id": 1,
        "displaySequence": 1,
        "alias": "seven",
        "description": "seven",
        "nomisPayBand": 7,
        "prisonCode": "MDI"
      }
    },
    "allocatedBy": "SOME_USER",
    "allocatedTime": "2023-01-10T14:46:05.849Z",
    "scheduleDescription" : "description",
    "activitySummary" : "summary",
    "status": "ACTIVE"
  }
  """.trimIndent()
}

fun buildApiAllocationDeallocatedJsonResponse(id: Long = ALLOCATION_ID): String {
  return """
  {
    "id": $id,
    "prisonerNumber": "A1234AA",
    "bookingId": $NOMIS_BOOKING_ID,
    "scheduleId": $ACTIVITY_SCHEDULE_ID,
    "startDate": "2023-01-12",
    "endDate": "2023-01-13",
    "payRate": {
      "id": 123,
      "incentiveLevel": "1",
      "incentiveNomisCode": "BAS",
      "prisonPayBand": {
        "id": 1,
        "displaySequence": 1,
        "alias": "seven",
        "description": "seven",
        "nomisPayBand": 7,
        "prisonCode": "MDI"
      }
    },
    "allocatedBy": "SOME_USER",
    "allocatedTime": "2023-01-10T14:46:05.849Z",
    "deallocatedBy": "ANOTHER_USER",
    "deallocatedReason": {
      "code": "RELEASED",
      "description": "Released from prison"
    },
    "deallocatedTime": "2023-01-13T18:49:04.837Z",
    "scheduleDescription" : "description",
    "activitySummary" : "summary",
    "status": "ENDED"
  }
  """.trimIndent()
}

fun buildApiAllocationSuspendedJsonResponse(id: Long = ALLOCATION_ID): String {
  return """
  {
    "id": $id,
    "prisonerNumber": "A1234AA",
    "bookingId": $NOMIS_BOOKING_ID,
    "scheduleId": $ACTIVITY_SCHEDULE_ID,
    "startDate": "2023-01-12",
    "payRate": {
      "id": 123,
      "incentiveLevel": "1",
      "incentiveNomisCode": "BAS",
      "prisonPayBand": {
        "id": 1,
        "displaySequence": 1,
        "alias": "seven",
        "description": "seven",
        "nomisPayBand": 7,
        "prisonCode": "MDI"
      }
    },
    "allocatedBy": "SOME_USER",
    "allocatedTime": "2023-01-10T14:46:05.849Z",
    "suspendedBy": "SUSPEND_USER",
    "suspendedReason": "HOSPITAL",
    "suspendedTime": "2023-01-13T18:49:04.837Z",
    "scheduleDescription" : "description",
    "activitySummary" : "summary",
    "status": "SUSPENDED"
  }
  """.trimIndent()
}
