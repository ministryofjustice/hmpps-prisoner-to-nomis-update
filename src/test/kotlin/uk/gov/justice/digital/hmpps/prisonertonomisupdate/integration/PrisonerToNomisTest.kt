package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.UrlPattern
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCancelledMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension.Companion.visitsApi

class PrisonerToNomisTest : SqsIntegrationTestBase() {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Test
  fun `will consume a prison visits create message`() {
    visitsApi.stubVisitGet("12", buildVisitApiDtoJsonResponse(visitId = "12", prisonerId = "A32323Y"))
    nomisApi.stubVisitCreate(prisonerId = "A32323Y")

    val message = prisonVisitCreatedMessage(prisonerId = "A32323Y")

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { visitsApi.getCountFor("/visits/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/prisoners/A32323Y/visits") } matches { it == 1 }

    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/A32323Y/visits"))
        .withRequestBody(WireMock.matchingJsonPath("offenderNo", WireMock.equalTo("A32323Y")))
        .withRequestBody(WireMock.matchingJsonPath("vsipVisitId", WireMock.equalTo("12")))
        .withRequestBody(WireMock.matchingJsonPath("prisonId", WireMock.equalTo("MDI")))
        .withRequestBody(WireMock.matchingJsonPath("visitType", WireMock.equalTo("SCON")))
        // .withRequestBody(WireMock.matchingJsonPath("visitRoomId", WireMock.equalTo("Room 1")))
        .withRequestBody(WireMock.matchingJsonPath("startDateTime", WireMock.equalTo("2019-12-02T09:00:00")))
        .withRequestBody(WireMock.matchingJsonPath("issueDate", WireMock.equalTo("2021-03-05")))
    )
  }

  @Test
  fun `will consume a prison visits cancel message`() {

    nomisApi.stubVisitCancel(prisonerId = "AB12345", visitId = "12")

    val message = prisonVisitCancelledMessage(prisonerId = "AB12345")

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { nomisApi.putCountFor("/prisoners/AB12345/visits/vsipVisitId/12/cancel") } matches { it == 1 }

    nomisApi.verify(
      WireMock.putRequestedFor(WireMock.urlEqualTo("/prisoners/AB12345/visits/vsipVisitId/12/cancel"))
        .withRequestBody(WireMock.matchingJsonPath("outcome", WireMock.equalTo("VISCANC")))
    )

    visitsApi.verify(
      0,
      WireMock.getRequestedFor(UrlPattern.ANY)
    )
  }

  fun buildVisitApiDtoJsonResponse(visitId: String = "1", prisonerId: String = "A32323Y"): String {
    return """
    {
      "visitId": "$visitId",
      "prisonId": "MDI",
      "prisonerId": "$prisonerId",
      "visitType": "STANDARD_SOCIAL",
      "visitDate": "2019-12-02",
      "startTime": "09:00:00",
      "endTime": "10:00:00",
      "currentStatus": "BOOKED",
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

  private fun getNumberOfMessagesCurrentlyOnQueue(): Int? {
    val queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    val messagesOnQueue = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    log.info("Number of messages on prisoner queue: $messagesOnQueue")
    return messagesOnQueue
  }
}
