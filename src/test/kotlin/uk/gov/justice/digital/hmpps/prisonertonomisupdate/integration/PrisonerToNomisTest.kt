package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCancelledMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitCreatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitUpdatedMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits.PrisonVisitsService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension.Companion.visitsApi

class PrisonerToNomisTest : SqsIntegrationTestBase() {

  @SpyBean
  private lateinit var prisonVisitsService: PrisonVisitsService

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Test
  fun `will consume a prison visits create message`() {
    visitsApi.stubVisitGet("12", buildVisitApiDtoJsonResponse(prisonerId = "A32323Y"))
    nomisApi.stubVisitCreate()
    visitsApi.stubVisitMappingPut("12")

    val message = prisonVisitCreatedMessage()

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { visitsApi.getCountFor("/visits/12") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/visits") } matches { it == 1 }
    await untilCallTo { visitsApi.putCountFor("/visits/12/nomis-mapping") } matches { it == 1 }

    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/visits"))
        .withRequestBody(WireMock.matchingJsonPath("offenderNo", WireMock.equalTo("A32323Y")))
        .withRequestBody(WireMock.matchingJsonPath("prisonId", WireMock.equalTo("MDI")))
        .withRequestBody(WireMock.matchingJsonPath("visitType", WireMock.equalTo("SCON")))
        .withRequestBody(WireMock.matchingJsonPath("visitRoomId", WireMock.equalTo("Room 1")))
        .withRequestBody(WireMock.matchingJsonPath("startTime", WireMock.equalTo("2019-12-02T09:00:00")))
        .withRequestBody(WireMock.matchingJsonPath("issueDate", WireMock.equalTo("2021-03-05")))
    )
    visitsApi.verify(
      WireMock.putRequestedFor(WireMock.urlEqualTo("/visits/12/nomis-mapping"))
        .withRequestBody(WireMock.matchingJsonPath("nomisVisitId", WireMock.equalTo("12345")))
    )
  }

  @Test
  fun `will consume a prison visits cancel message`() {

    val message = prisonVisitCancelledMessage()

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    verify(prisonVisitsService).cancelVisit()
  }

  @Test
  fun `will consume a prison visits update message`() {

    val message = prisonVisitUpdatedMessage()

    awsSqsClient.sendMessage(queueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    verify(prisonVisitsService).updateVisit()
  }

  fun buildVisitApiDtoJsonResponse(visitId: String = "1", prisonerId: String = "A32323Y"): String {
    return """
    {
      "visitId": "$visitId",
      "prisonId": "MDI",
      "prisonerId": "$prisonerId",
      "visitType": "STANDARD_SOCIAL",
      "visitRoom": "Room 1",
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
