package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.prisonVisitMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension.Companion.visitsApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

class VisitToNomisTest : SqsIntegrationTestBase() {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Nested
  inner class CreateVisit {

    @Test
    fun `will consume a prison visits create message`() {
      visitsApi.stubVisitGet(
        "12",
        buildVisitApiDtoJsonResponse(
          visitId = "12",
          prisonerId = "A32323Y",
          visitRoom = "Main visits room",
          visitRestriction = "OPEN",
        ),
      )
      mappingServer.stubGetVsipWithError("12", 404)
      mappingServer.stubCreate()
      nomisApi.stubVisitCreate(prisonerId = "A32323Y")

      publishToDomainEvent("prison-visit.booked", prisonerId = "AB12345")

      await untilCallTo { awsSqsVisitClient.countAllMessagesOnQueue(visitQueueUrl).get() } matches { it == 0 }
      await untilCallTo { visitsApi.getCountFor("/visits/12") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/prisoners/A32323Y/visits") } matches { it == 1 }

      nomisApi.verify(
        postRequestedFor(urlEqualTo("/prisoners/A32323Y/visits"))
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
              equalTo("DPS booking reference: 12"),
            ),
          )
          .withRequestBody(
            matchingJsonPath(
              "visitOrderComment",
              equalTo("DPS booking reference: 12"),
            ),
          ),
      )

      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/visits"))
          .withRequestBody(matchingJsonPath("nomisId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("vsipId", equalTo("12")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("ONLINE"))),
      )
    }

    @Nested
    inner class WhenDuplicateVisitDetectedInNomis {
      val dpsVisitId = "12"
      val nomisVisitId = 7655L

      @BeforeEach
      fun setUp() {
        visitsApi.stubVisitGet(dpsVisitId, buildVisitApiDtoJsonResponse(visitId = "12", prisonerId = "A32323Y"))
        nomisApi.stubVisitCreateWithDuplicateError(prisonerId = "A32323Y", nomisVisitId = nomisVisitId)
      }

      @Nested
      inner class WhenMappingStillDoesNotExist {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetVsipWithError(dpsVisitId, 404)
          mappingServer.stubCreate()
          publishToDomainEvent("prison-visit.booked", prisonerId = "AB12345").also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `mapping will be created using NOMIS id from original visit`() {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/visits"))
              .withRequestBody(matchingJsonPath("nomisId", equalTo("$nomisVisitId")))
              .withRequestBody(matchingJsonPath("vsipId", equalTo(dpsVisitId)))
              .withRequestBody(matchingJsonPath("mappingType", equalTo("ONLINE"))),
          )
        }

        @Test
        fun `no message will be added to DLQ`() {
          await untilCallTo { awsSqsVisitClient.countMessagesOnQueue(visitDlqUrl!!).get() } matches { it == 0 }
        }
      }

      @Nested
      inner class WhenMappingWasCreatedWhileDuplicateWasDetected {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetVsipWithErrorFollowedBySuccess(
            dpsVisitId,
            response =
            """
              { 
                "nomisId": "$nomisVisitId",
                "vsipId": "$dpsVisitId",
                "mappingType": "ONLINE"
              }
            """,
            status = 404,
          )
          publishToDomainEvent("prison-visit.booked", prisonerId = "AB12345").also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `no mapping will be created`() {
          mappingServer.verify(
            0,
            postRequestedFor(urlEqualTo("/mapping/visits")),
          )
        }

        @Test
        fun `no message will be added to DLQ`() {
          await untilCallTo { awsSqsVisitClient.countMessagesOnQueue(visitDlqUrl!!).get() } matches { it == 0 }
        }
      }
    }

    @Test
    fun `will retry after a mapping failure`() {
      visitsApi.stubVisitGet("12", buildVisitApiDtoJsonResponse(visitId = "12", prisonerId = "A32323Y"))
      mappingServer.stubGetVsipWithError("12", 404)
      nomisApi.stubVisitCreate(prisonerId = "A32323Y")
      mappingServer.stubCreateWithError()

      publishToDomainEvent("prison-visit.booked", prisonerId = "AB12345")

      await untilCallTo { visitsApi.getCountFor("/visits/12") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/prisoners/A32323Y/visits") } matches { it == 1 }

      // the mapping call fails resulting in a retry message being queued
      // the retry message is processed and fails resulting in a message on the DLQ after 1 attempt
      await untilCallTo { awsSqsVisitClient.countMessagesOnQueue(visitDlqUrl!!).get() } matches { it == 1 }

      // Next time the retry will succeed
      mappingServer.stubCreate()

      webTestClient.put()
        .uri("/queue-admin/retry-all-dlqs")
        .exchange()
        .expectStatus()
        .isOk

      await untilCallTo { mappingServer.postCountFor("/mapping/visits") } matches { it == 3 } // 1 initial call, 1 retries and 1 final successful call
      await untilCallTo { awsSqsVisitClient.countAllMessagesOnQueue(visitQueueUrl).get() } matches { it == 0 }
      await untilCallTo { awsSqsVisitClient.countMessagesOnQueue(visitDlqUrl!!).get() } matches {
        log.trace("Messages on queue: {}", it)
        it == 0
      }
    }

    @Test
    fun `will log when duplicate mapping is detected`() {
      visitsApi.stubVisitGet("12", buildVisitApiDtoJsonResponse(visitId = "12", prisonerId = "A32323Y"))
      mappingServer.stubGetVsipWithError("12", 404)
      nomisApi.stubVisitCreate(prisonerId = "A32323Y")
      mappingServer.stubCreateWithDuplicateError(visitId = "12", nomisId = 12345, duplicateNomisId = 54321)

      publishToDomainEvent("prison-visit.booked", prisonerId = "AB12345")

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          Mockito.eq("visit-mapping-create-failed"),
          any(),
          isNull(),
        )
      }

      await untilCallTo { visitsApi.getCountFor("/visits/12") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/prisoners/A32323Y/visits") } matches { it == 1 }

      // the mapping call fails but is not queued for retry
      await untilCallTo { awsSqsVisitClient.countMessagesOnQueue(visitDlqUrl!!).get() } matches { it == 0 }

      await untilCallTo { mappingServer.postCountFor("/mapping/visits") } matches { it == 1 } // only tried once

      verify(telemetryClient).trackEvent(
        Mockito.eq("to-nomis-synch-visit-duplicate"),
        org.mockito.kotlin.check {
          Assertions.assertThat(it["existingVsipId"]).isEqualTo("12")
          Assertions.assertThat(it["existingNomisId"]).isEqualTo("12345")
          Assertions.assertThat(it["duplicateVsipId"]).isEqualTo("12")
          Assertions.assertThat(it["duplicateNomisId"]).isEqualTo("54321")
        },
        isNull(),
      )
    }
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
      """.trimIndent(),
    )

    visitsApi.stubVisitGet(
      "12",
      buildVisitApiDtoJsonResponse(visitId = "12", prisonerId = "A32323Y", outcome = "PRISONER_CANCELLED"),
    )
    nomisApi.stubVisitCancel(prisonerId = "AB12345", visitId = "456")

    publishToDomainEvent("prison-visit.cancelled", prisonerId = "AB12345")

    await untilCallTo { awsSqsVisitClient.countMessagesOnQueue(visitQueueUrl).get() } matches { it == 0 }
    await untilCallTo { nomisApi.putCountFor("/prisoners/AB12345/visits/456/cancel") } matches { it == 1 }

    nomisApi.verify(
      putRequestedFor(urlEqualTo("/prisoners/AB12345/visits/456/cancel"))
        .withRequestBody(matchingJsonPath("outcome", equalTo("OFFCANC"))),
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
      """.trimIndent(),
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
        visitors = listOf(99, 88),
      ),
    )
    nomisApi.stubVisitUpdate(prisonerId = "AB12345", visitId = "456")

    publishToDomainEvent("prison-visit.changed", prisonerId = "AB12345")

    await untilCallTo { awsSqsVisitClient.countMessagesOnQueue(visitQueueUrl).get() } matches { it == 0 }
    await untilCallTo { nomisApi.putCountFor("/prisoners/AB12345/visits/456") } matches { it == 1 }

    nomisApi.verify(
      putRequestedFor(urlEqualTo("/prisoners/AB12345/visits/456"))
        .withRequestBody(matchingJsonPath("startDateTime", equalTo("2021-03-05T09:00:00")))
        .withRequestBody(matchingJsonPath("endTime", equalTo("10:00:00")))
        .withRequestBody(matchingJsonPath("visitorPersonIds[0]", equalTo("99")))
        .withRequestBody(matchingJsonPath("visitorPersonIds[1]", equalTo("88")))
        .withRequestBody(matchingJsonPath("room", equalTo("Side Room")))
        .withRequestBody(matchingJsonPath("openClosedStatus", equalTo("CLOSED"))),
    )
  }

  fun publishToDomainEvent(eventType: String, prisonerId: String) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(prisonVisitMessagePayload(eventType = eventType, prisonerId = prisonerId))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}

fun buildVisitApiDtoJsonResponse(
  visitId: String = "1",
  prisonerId: String = "A32323Y",
  outcome: String? = null,
  visitRoom: String = "Main visits room",
  visitRestriction: String = "OPEN",
  startTimestamp: String = "2019-12-02T09:00:00",
  endTimestamp: String = "2019-12-02T10:00:00",
  visitors: List<Long> = listOf(543524, 344444, 655656),
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
