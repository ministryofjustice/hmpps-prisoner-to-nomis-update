package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.getNumberOfMessagesCurrentlyOnQueue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCancelledMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitChangedMessage
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
      WireMock.postRequestedFor(urlEqualTo("/prisoners/A32323Y/visits"))
        .withRequestBody(matchingJsonPath("offenderNo", equalTo("A32323Y")))
        .withRequestBody(matchingJsonPath("prisonId", equalTo("MDI")))
        .withRequestBody(matchingJsonPath("visitType", equalTo("SCON")))
        .withRequestBody(matchingJsonPath("room", equalTo("Main visits room")))
        .withRequestBody(matchingJsonPath("openClosedStatus", equalTo("OPEN")))
        .withRequestBody(matchingJsonPath("startDateTime", equalTo("2019-12-02T09:00:00")))
        .withRequestBody(matchingJsonPath("issueDate", equalTo("2021-03-05")))
        .withRequestBody(
          matchingJsonPath(
            "visitComment",
            equalTo("Created by Book A Prison Visit. Reference: 12")
          )
        )
        .withRequestBody(
          matchingJsonPath(
            "visitOrderComment",
            equalTo("Created by Book A Prison Visit for visit with reference: 12")
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

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, visitDlqUrl!!) } matches { it == 1 }

    // Next time the retry will succeed
    mappingServer.stubCreate()

    webTestClient.put()
      .uri("/queue-admin/retry-all-dlqs")
      .exchange()
      .expectStatus()
      .isOk

    await untilCallTo { mappingServer.postCountFor("/mapping/visits") } matches { it == 7 } // 1 initial call, 5 retries and 1 final successful call
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, visitQueueUrl) } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, visitDlqUrl!!) } matches { it == 0 }
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
      putRequestedFor(urlEqualTo("/prisoners/AB12345/visits/456/cancel"))
        .withRequestBody(matchingJsonPath("outcome", equalTo("OFFCANC")))
    )
  }

  @Test
  fun `will consume a prison visits changed message`() {

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
      buildVisitApiDtoJsonResponse(
        visitId = "12",
        prisonerId = "A32323Y",
        visitRoom = "Side Room",
        visitRestriction = "CLOSED",
        startTimestamp = "2021-03-05T09:00:00",
        endTimestamp = "2021-03-05T10:00:00",
        visitors = listOf(99, 88)
      )
    )
    nomisApi.stubVisitUpdate(prisonerId = "AB12345", visitId = "456")

    val message = prisonVisitChangedMessage(prisonerId = "AB12345")

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue(awsSqsClient, queueUrl) } matches { it == 0 }
    await untilCallTo { nomisApi.putCountFor("/prisoners/AB12345/visits/456") } matches { it == 1 }

    nomisApi.verify(
      putRequestedFor(urlEqualTo("/prisoners/AB12345/visits/456"))
        .withRequestBody(matchingJsonPath("startDateTime", equalTo("2021-03-05T09:00:00")))
        .withRequestBody(matchingJsonPath("endTime", equalTo("10:00:00")))
        .withRequestBody(matchingJsonPath("visitorPersonIds[0]", equalTo("99")))
        .withRequestBody(matchingJsonPath("visitorPersonIds[1]", equalTo("88")))
        .withRequestBody(matchingJsonPath("room", equalTo("Side Room")))
        .withRequestBody(matchingJsonPath("openClosedStatus", equalTo("CLOSED")))
    )
  }

  fun buildVisitApiDtoJsonResponse(
    visitId: String = "1",
    prisonerId: String = "A32323Y",
    outcome: String? = null,
    visitRoom: String = "Main visits room",
    visitRestriction: String = "OPEN",
    startTimestamp: String = "2019-12-02T09:00:00",
    endTimestamp: String = "2019-12-02T10:00:00",
    visitors: List<Long> = listOf(543524, 344444, 655656)
  ): String {
    val outcomeString = outcome?.let { "\"outcomeStatus\": \"$it\"," } ?: ""

    return """
    {
      "reference": "$visitId",
      "prisonId": "MDI",
      "prisonerId": "$prisonerId",
      "visitType": "SOCIAL",
      "startTimestamp": "$startTimestamp",
      "endTimestamp": "$endTimestamp",
      "visitRestriction": "$visitRestriction",
      "visitRoom": "$visitRoom",
      $outcomeString
      "visitStatus": "BOOKED",
      "visitors": [ ${visitors.joinToString(",") { "{\"nomisPersonId\": $it}" }} ]
    }
    """.trimIndent()
  }
}
