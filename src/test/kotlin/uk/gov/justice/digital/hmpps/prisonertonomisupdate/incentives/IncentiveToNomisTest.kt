package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.incentiveMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

class IncentiveToNomisTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a incentives create message`() {
    incentivesApi.stubIncentiveGet(12, buildIncentiveApiDtoJsonResponse())
    mappingServer.stubGetIncentiveIdWithError(12, 404)
    mappingServer.stubCreateIncentive()
    nomisApi.stubIncentiveCreate(bookingId = 456)

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(incentiveMessagePayload(12))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("incentives.iep-review.inserted").build(),
          ),
        ).build(),
    ).get()

    await untilCallTo { awsSqsIncentiveClient.countMessagesOnQueue(incentiveQueueUrl).get() } matches { it == 0 }
    await untilCallTo { incentivesApi.getCountFor("/incentive-reviews/id/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/prisoners/booking-id/456/incentives") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/booking-id/456/incentives"))
        .withRequestBody(WireMock.matchingJsonPath("iepDateTime", WireMock.equalTo("2022-12-02T10:00:00")))
        .withRequestBody(WireMock.matchingJsonPath("prisonId", WireMock.equalTo("MDI")))
        .withRequestBody(WireMock.matchingJsonPath("iepLevel", WireMock.equalTo("STD"))),
    )
    mappingServer.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/incentives"))
        .withRequestBody(WireMock.matchingJsonPath("nomisBookingId", WireMock.equalTo("456")))
        .withRequestBody(WireMock.matchingJsonPath("nomisIncentiveSequence", WireMock.equalTo("1")))
        .withRequestBody(WireMock.matchingJsonPath("incentiveId", WireMock.equalTo("12")))
        .withRequestBody(WireMock.matchingJsonPath("mappingType", WireMock.equalTo("INCENTIVE_CREATED"))),
    )
  }

  @Test
  fun `will retry after a mapping failure`() {
    incentivesApi.stubIncentiveGet(12, buildIncentiveApiDtoJsonResponse())
    mappingServer.stubGetIncentiveIdWithError(12, 404)
    nomisApi.stubIncentiveCreate(bookingId = 456)
    mappingServer.stubCreateIncentiveWithError()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(incentiveMessagePayload(12))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("incentives.iep-review.inserted").build(),
          ),
        ).build(),
    ).get()

    await untilCallTo { incentivesApi.getCountFor("/incentive-reviews/id/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/prisoners/booking-id/456/incentives") } matches { it == 1 }

    // the mapping call fails resulting in a retry message being queued
    // the retry message is processed and fails resulting in a message on the DLQ after 2 attempts
    await untilCallTo { awsSqsIncentiveDlqClient!!.countAllMessagesOnQueue(incentiveDlqUrl!!).get() } matches { it == 1 }

    // Next time the retry will succeed
    mappingServer.stubCreateIncentive()

    webTestClient.put()
      .uri("/queue-admin/retry-all-dlqs")
      .exchange()
      .expectStatus()
      .isOk

    await untilCallTo { mappingServer.postCountFor("/mapping/incentives") } matches { it == 4 } // 1 initial call, 2 retries and 1 final successful call
    await untilCallTo { awsSqsIncentiveClient.countAllMessagesOnQueue(incentiveQueueUrl).get() } matches { it == 0 }
    await untilCallTo { awsSqsIncentiveClient.countAllMessagesOnQueue(incentiveDlqUrl!!).get() } matches { it == 0 }
  }

  @Test
  fun `will log when duplicate is detected`() {
    incentivesApi.stubIncentiveGet(12, buildIncentiveApiDtoJsonResponse())
    mappingServer.stubGetIncentiveIdWithError(12, 404)
    nomisApi.stubIncentiveCreate(bookingId = 456)
    mappingServer.stubCreateIncentiveWithDuplicateError(incentiveId = 12, nomisBookingId = 456, nomisIncentiveSequence = 1, duplicateNomisIncentiveSequence = 2)

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(incentiveMessagePayload(12))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("incentives.iep-review.inserted").build(),
          ),
        ).build(),
    ).get()

    await untilAsserted {
      verify(telemetryClient).trackEvent(
        Mockito.eq("incentive-mapping-create-failed"),
        any(),
        isNull(),
      )
    }
    await untilCallTo { incentivesApi.getCountFor("/incentive-reviews/id/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/prisoners/booking-id/456/incentives") } matches { it == 1 }

    // the mapping call fails but is not queued for retry
    await untilCallTo { awsSqsIncentiveDlqClient!!.countAllMessagesOnQueue(incentiveDlqUrl!!).get() } matches { it == 0 }

    await untilCallTo { mappingServer.postCountFor("/mapping/incentives") } matches { it == 1 } // only tried once

    verify(telemetryClient).trackEvent(
      Mockito.eq("to-nomis-synch-incentive-duplicate"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["existingIncentiveId"]).isEqualTo("12")
        Assertions.assertThat(it["existingNomisBookingId"]).isEqualTo("456")
        Assertions.assertThat(it["existingNomisIncentiveSequence"]).isEqualTo("1")
        Assertions.assertThat(it["duplicateIncentiveId"]).isEqualTo("12")
        Assertions.assertThat(it["duplicateNomisBookingId"]).isEqualTo("456")
        Assertions.assertThat(it["duplicateNomisIncentiveSequence"]).isEqualTo("2")
      },
      isNull(),
    )
  }

  fun buildIncentiveApiDtoJsonResponse(id: Long = 12): String =
    """
    {
      "id": $id,
      "iepCode": "STD",
      "iepLevel": "Standard",
      "bookingId": 456,
      "userId": "BILLYBOB",
      "prisonerNumber": "A1234AA",
      "sequence": 2,
      "iepDate": "2022-12-02",
      "iepTime": "2022-12-02T10:00:00",
      "agencyId": "MDI",
      "reviewType": "INITIAL",
      "auditModuleName": "audit",
      "isRealReview": true
    }
    """.trimIndent()
}
