package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.getNumberOfMessagesCurrentlyOnQueue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCancelledMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension.Companion.visitsApi

class VisitToNomisTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a prison visits create message`() {
    visitsApi.stubVisitGet(
      "12",
      buildVisitApiDtoJsonResponse(
        visitId = "12",
        prisonerId = "A32323Y",
        visitRoom = "Main visits room",
        visitRestriction = "OPEN",
      )
    )
    mappingServer.stubGetVsipWithError("12", 404)
    mappingServer.stubCreate()
    nomisApi.stubVisitCreate(prisonerId = "A32323Y")

    val message = prisonVisitCreatedMessage(prisonerId = "A32323Y")

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, queueUrl) } matches { it == 0 }
    await untilCallTo { visitsApi.getCountFor("/visits/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/prisoners/A32323Y/visits") } matches { it == 1 }

    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/A32323Y/visits"))
        .withRequestBody(WireMock.matchingJsonPath("offenderNo", WireMock.equalTo("A32323Y")))
        .withRequestBody(WireMock.matchingJsonPath("prisonId", WireMock.equalTo("MDI")))
        .withRequestBody(WireMock.matchingJsonPath("visitType", WireMock.equalTo("SCON")))
        .withRequestBody(WireMock.matchingJsonPath("room", WireMock.equalTo("Main visits room")))
        .withRequestBody(WireMock.matchingJsonPath("openClosedStatus", WireMock.equalTo("OPEN")))
        .withRequestBody(WireMock.matchingJsonPath("startDateTime", WireMock.equalTo("2019-12-02T09:00:00")))
        .withRequestBody(WireMock.matchingJsonPath("issueDate", WireMock.equalTo("2021-03-05")))
        .withRequestBody(
          WireMock.matchingJsonPath(
            "visitComment",
            WireMock.equalTo("Created by Book A Prison Visit. Reference: 12")
          )
        )
        .withRequestBody(
          WireMock.matchingJsonPath(
            "visitOrderComment",
            WireMock.equalTo("Created by Book A Prison Visit for visit with reference: 12")
          )
        )
    )
  }

  @Test
  fun `will retry after a mapping failure`() {
    visitsApi.stubVisitGet("12", buildVisitApiDtoJsonResponse(visitId = "12", prisonerId = "A32323Y"))
    mappingServer.stubGetVsipWithError("12", 404)
    nomisApi.stubVisitCreate(prisonerId = "A32323Y")
    mappingServer.stubCreateWithError()

    val message = prisonVisitCreatedMessage(prisonerId = "A32323Y")

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { visitsApi.getCountFor("/visits/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/prisoners/A32323Y/visits") } matches { it == 1 }

    // the mapping call fails resulting in a retry message being queued
    // the retry message is processed and fails resulting in a message on the DLQ after 5 attempts

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, dlqUrl!!) } matches { it == 1 }

    // Next time the retry will succeed
    mappingServer.stubCreate()

    webTestClient.put()
      .uri("/queue-admin/retry-all-dlqs")
      .exchange()
      .expectStatus()
      .isOk

    await untilCallTo { mappingServer.postCountFor("/mapping") } matches { it == 7 } // 1 initial call, 5 retries and 1 final successful call
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, queueUrl) } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, dlqUrl!!) } matches { it == 0 }
  }

  @Test
  fun `will consume a prison visits cancel message`() {

    mappingServer.stubGetVsip(
      "12",
      response = """{ 
          "nomisId": "456",
          "vsipId": "12",
          "mappingType": "ONLINE"
        }
      """.trimIndent()
    )

    visitsApi.stubVisitGet(
      "12",
      buildVisitApiDtoJsonResponse(visitId = "12", prisonerId = "A32323Y", outcome = "PRISONER_CANCELLED")
    )
    nomisApi.stubVisitCancel(prisonerId = "AB12345", visitId = "456")

    val message = prisonVisitCancelledMessage(prisonerId = "AB12345")

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, queueUrl) } matches { it == 0 }
    await untilCallTo { nomisApi.putCountFor("/prisoners/AB12345/visits/456/cancel") } matches { it == 1 }

    nomisApi.verify(
      WireMock.putRequestedFor(WireMock.urlEqualTo("/prisoners/AB12345/visits/456/cancel"))
        .withRequestBody(WireMock.matchingJsonPath("outcome", WireMock.equalTo("OFFCANC")))
    )
  }

  fun buildVisitApiDtoJsonResponse(
    visitId: String = "1",
    prisonerId: String = "A32323Y",
    outcome: String? = null,
    visitRoom: String = "Main visits room",
    visitRestriction: String = "OPEN",
  ): String {
    val outcomeString = outcome?.let { "\"outcomeStatus\": \"$it\"," } ?: ""

    return """
    {
      "reference": "$visitId",
      "prisonId": "MDI",
      "prisonerId": "$prisonerId",
      "visitType": "SOCIAL",
      "startTimestamp": "2019-12-02T09:00:00",
      "endTimestamp": "2019-12-02T10:00:00",
      "visitRestriction": "$visitRestriction",
      "visitRoom": "$visitRoom",
      $outcomeString
      "visitStatus": "BOOKED",
      "visitors": [
        {
          "nomisPersonId": 543524
        },
        {
          "nomisPersonId": 344444
        },
        {
          "nomisPersonId": 655656
        }
      ]
    }
    """.trimIndent()
  }
}
