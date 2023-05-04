package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubAllocationCreate(NOMIS_CRS_ACTY_ID)

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
      await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/activities/$NOMIS_CRS_ACTY_ID/allocations") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        WireMock.postRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocations"))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("$NOMIS_BOOKING_ID")))
          .withRequestBody(matchingJsonPath("startDate", equalTo("2023-01-12")))
          .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("payBandCode", equalTo("7"))),
      )
    }
  }

  @Nested
  inner class DeallocatePrisoner {
    @Test
    fun `will consume a deallocation message`() {
      ActivitiesApiExtension.activitiesApi.stubGetAllocation(ALLOCATION_ID, buildApiAllocationDtoJsonResponse())
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleId(ACTIVITY_SCHEDULE_ID, buildGetMappingResponse())
      NomisApiExtension.nomisApi.stubDeallocate(NOMIS_CRS_ACTY_ID)

      awsSnsClient.publish(
        PublishRequest.builder().topicArn(topicArn)
          .message(allocationMessagePayload("activities.prisoner.deallocated", ACTIVITY_SCHEDULE_ID, ALLOCATION_ID))
          .messageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue.builder().dataType("String")
                .stringValue("activities.prisoner.deallocated").build(),
            ),
          ).build(),
      ).get()

      await untilCallTo { ActivitiesApiExtension.activitiesApi.getCountFor("/allocations/id/$ALLOCATION_ID") } matches { it == 1 }
      await untilCallTo { NomisApiExtension.nomisApi.putCountFor("/activities/$NOMIS_CRS_ACTY_ID/allocations") } matches { it == 1 }
      NomisApiExtension.nomisApi.verify(
        WireMock.putRequestedFor(urlEqualTo("/activities/$NOMIS_CRS_ACTY_ID/allocations"))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("$NOMIS_BOOKING_ID")))
          .withRequestBody(matchingJsonPath("endDate", equalTo("2023-01-13")))
          .withRequestBody(matchingJsonPath("endReason", equalTo("PRG_END")))
          .withRequestBody(matchingJsonPath("endComment", equalTo("End date reached"))),
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
    "endDate": "2023-01-13",
    "prisonPayBand": {
      "id": 1,
      "displaySequence": 1,
      "alias": "seven",
      "description": "seven",
      "nomisPayBand": 7,
      "prisonCode": "MDI"
    },
    "deallocatedReason": "End date reached",
    "scheduleDescription" : "description",
    "activitySummary" : "summary"
  }
  """.trimIndent()
}
