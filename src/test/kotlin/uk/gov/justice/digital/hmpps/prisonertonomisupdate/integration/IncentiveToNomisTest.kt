package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.getNumberOfMessagesCurrentlyOnQueue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.incentiveCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

class IncentiveToNomisTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a incentives create message`() {
    incentivesApi.stubIncentiveGet(12, buildIncentiveApiDtoJsonResponse())
    mappingServer.stubGetIncentiveIdWithError(12, 404)
    mappingServer.stubCreateIncentive()
    nomisApi.stubIncentiveCreate(bookingId = 456)

    val message = incentiveCreatedMessage(12)

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, queueUrl) } matches { it == 0 }
    await untilCallTo { incentivesApi.getCountFor("/iep/reviews/id/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/prisoners/booking-id/456/incentives") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/booking-id/456/incentives"))
        .withRequestBody(WireMock.matchingJsonPath("iepDateTime", WireMock.equalTo("2022-12-02T10:00:00")))
        .withRequestBody(WireMock.matchingJsonPath("prisonId", WireMock.equalTo("MDI")))
        .withRequestBody(WireMock.matchingJsonPath("iepLevel", WireMock.equalTo("STD")))
    )
  }

  @Test
  fun `will retry after a mapping failure`() {
    incentivesApi.stubIncentiveGet(12, buildIncentiveApiDtoJsonResponse())
    mappingServer.stubGetIncentiveIdWithError(12, 404)
    nomisApi.stubIncentiveCreate(bookingId = 456)
    mappingServer.stubCreateIncentiveWithError()

    val message = incentiveCreatedMessage(12)

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { incentivesApi.getCountFor("/iep/reviews/id/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/prisoners/booking-id/456/incentives") } matches { it == 1 }

    // the mapping call fails resulting in a retry message being queued
    // the retry message is processed and fails resulting in a message on the DLQ after 5 attempts

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, dlqUrl!!) } matches { it == 1 }

    // Next time the retry will succeed
    mappingServer.stubCreateIncentive()

    webTestClient.put()
      .uri("/queue-admin/retry-all-dlqs")
      .exchange()
      .expectStatus()
      .isOk

    await untilCallTo { mappingServer.postCountFor("/mapping/incentives") } matches { it == 7 } // 1 initial call, 5 retries and 1 final successful call
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, queueUrl) } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, dlqUrl!!) } matches { it == 0 }
  }

  fun buildIncentiveApiDtoJsonResponse(id: Long = 12): String =
    """
    {
      "id": $id,
      "iepCode": "STD",
      "iepLevel": "Standard",
      "bookingId": 456,
      "prisonerNumber": "A1234AA",
      "sequence": 2,
      "iepDate": "2022-12-02",
      "iepTime": "2022-12-02T10:00:00",
      "agencyId": "MDI"
    }
    """.trimIndent()
}
