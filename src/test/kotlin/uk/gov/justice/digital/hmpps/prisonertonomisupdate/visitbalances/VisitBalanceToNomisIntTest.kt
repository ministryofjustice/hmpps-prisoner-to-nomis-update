package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.VisitAllocationPrisonerAdjustmentResponseDto.ChangeLogSource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceDpsApiExtension.Companion.visitBalanceDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

class VisitBalanceToNomisIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var visitBalanceNomisApi: VisitBalanceNomisApiMockServer

  @Nested
  @DisplayName("prison-visit-allocation.adjustment.created")
  inner class VisitBalanceAdjustmentCreated {
    val visitBalanceAdjId = "a77fa39f-49cf-4e07-af09-f47cfdb3c6ef"

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setup() {
        visitBalanceDpsApi.stubGetVisitBalanceAdjustment(visitBalanceAdjId)
        visitBalanceNomisApi.stubPostVisitBalanceAdjustment()
        sendVisitBalanceAdjustmentToQueue()
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the adjustment details from Dps`() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/adjustment/$visitBalanceAdjId")))
      }

      @Test
      fun `will create the adjustment in Nomis`() {
        visitBalanceNomisApi.verify(
          postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/visit-balance-adjustments"))
            .withRequestBodyJsonPath("adjustmentDate", "2021-01-18")
            .withRequestBodyJsonPath("previousVisitOrderCount", 12)
            .withRequestBodyJsonPath("visitOrderChange", 2)
            .withRequestBodyJsonPath("previousPrivilegedVisitOrderCount", 7)
            .withRequestBodyJsonPath("privilegedVisitOrderChange", -1)
            .withRequestBodyJsonPath("comment", "A comment")
            .withRequestBody(matchingJsonPath("authorisedUsername", absent())),
        )
      }

      @Test
      fun `will send telemetry event showing the create success`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-created-success"),
          check {
            assertThat(it).containsEntry("visitBalanceAdjustmentId", "a77fa39f-49cf-4e07-af09-f47cfdb3c6ef")
            assertThat(it).containsEntry("prisonNumber", "A1234KT")
            assertThat(it).containsEntry("visitBalanceChange", "2")
            assertThat(it).containsEntry("privilegeVisitBalanceChange", "-1")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathForUserChange {

      @BeforeEach
      fun setup() {
        visitBalanceDpsApi.stubGetVisitBalanceAdjustment(
          visitBalanceAdjId,
          visitBalanceAdjustmentDto(
            visitBalanceAdjId,
            changeLogSource = ChangeLogSource.STAFF,
            userId = "SOME_USER",
          ),
        )
        visitBalanceNomisApi.stubPostVisitBalanceAdjustment()
        sendVisitBalanceAdjustmentToQueue()
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will create the adjustment in Nomis`() {
        visitBalanceNomisApi.verify(
          postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/visit-balance-adjustments"))
            .withRequestBodyJsonPath("adjustmentDate", "2021-01-18")
            .withRequestBodyJsonPath("previousVisitOrderCount", 12)
            .withRequestBodyJsonPath("visitOrderChange", 2)
            .withRequestBodyJsonPath("previousPrivilegedVisitOrderCount", 7)
            .withRequestBodyJsonPath("privilegedVisitOrderChange", -1)
            .withRequestBodyJsonPath("comment", "A comment")
            .withRequestBodyJsonPath("authorisedUsername", "SOME_USER"),
        )
      }
    }

    @Nested
    inner class HappyPathWithNomisFailures {

      @BeforeEach
      fun setUp() {
        visitBalanceDpsApi.stubGetVisitBalanceAdjustment(visitBalanceAdjId)
        visitBalanceNomisApi.stubPostVisitBalanceAdjustment(status = HttpStatus.INTERNAL_SERVER_ERROR)
        sendVisitBalanceAdjustmentToQueue()
        await untilCallTo {
          visitBalanceDlqClient!!.countAllMessagesOnQueue(visitBalanceDlqUrl!!).get()
        } matches { it == 1 }
      }

      @Test
      fun `will attempt to retrieve the balance adjustment from Dps `() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/adjustment/$visitBalanceAdjId")))
      }

      @Test
      fun `will not create telemetry tracking`() {
        verify(telemetryClient, never()).trackEvent(any(), any(), isNull())
      }

      @Test
      fun `will attempt call to Nomis several times and keep failing`() {
        visitBalanceNomisApi.verify(2, postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/visit-balance-adjustments")))
      }
    }

    @Nested
    inner class HappyPathWithDpsFailures {
      val bookingId = 123456L

      @BeforeEach
      fun setUp() {
        visitBalanceDpsApi.stubGetVisitBalanceAdjustment(status = HttpStatus.INTERNAL_SERVER_ERROR)
        sendVisitBalanceAdjustmentToQueue()
        await untilCallTo {
          visitBalanceDlqClient!!.countAllMessagesOnQueue(visitBalanceDlqUrl!!).get()
        } matches { it == 1 }
      }

      @Test
      fun `will attempt to retrieve visit balance adjustment from Dps`() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/adjustment/$visitBalanceAdjId")))
      }

      @Test
      fun `will not create telemetry tracking`() {
        verify(telemetryClient, never()).trackEvent(any(), any(), isNull())
      }

      @Test
      fun `will not attempt call to Nomis `() {
        visitBalanceNomisApi.verify(0, postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/visit-balance-adjustments")))
      }
    }
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.booking.moved")
  inner class BookingMoved {
    private val bookingId = 1234567L
    private val movedFromNomsNumber = "A1234AA"
    private val movedToNomsNumber = "B1234BB"
    private val bookingStartDateTime = "2021-07-05T10:55:04"

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setup() {
        nomisApi.stubCheckServicePrisonForPrisoner(prisonNumber = movedFromNomsNumber)
        nomisApi.stubCheckServicePrisonForPrisoner(prisonNumber = movedToNomsNumber)
        visitBalanceDpsApi.stubGetVisitBalance(PrisonerBalanceDto(prisonerId = movedFromNomsNumber, voBalance = 2, pvoBalance = 3))
        visitBalanceNomisApi.stubPutVisitBalance(prisonNumber = movedFromNomsNumber)
        visitBalanceNomisApi.stubPutVisitBalance(prisonNumber = movedToNomsNumber)
        publishBookingMovedEvent(bookingId, movedFromNomsNumber, movedToNomsNumber, bookingStartDateTime)
        waitForAnyProcessingToComplete(times = 2)
      }

      @Test
      fun `will retrieve the adjustment details from Dps`() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$movedFromNomsNumber/balance")))
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$movedToNomsNumber/balance")))
      }

      @Test
      fun `will create the adjustment in Nomis`() {
        visitBalanceNomisApi.verify(
          putRequestedFor(urlPathEqualTo("/prisoners/$movedFromNomsNumber/visit-balance"))
            .withRequestBodyJsonPath("remainingVisitOrders", "2")
            .withRequestBodyJsonPath("remainingPrivilegedVisitOrders", "3"),
        )
        visitBalanceNomisApi.verify(
          putRequestedFor(urlPathEqualTo("/prisoners/$movedToNomsNumber/visit-balance"))
            .withRequestBody(matchingJsonPath("remainingVisitOrders", absent()))
            .withRequestBody(matchingJsonPath("remainingPrivilegedVisitOrders", absent())),
        )
      }

      @Test
      fun `will send telemetry event showing the create success`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-booking-moved-from"),
          check {
            assertThat(it).containsEntry("bookingId", bookingId.toString())
            assertThat(it).containsEntry("prisonNumber", movedFromNomsNumber)
            assertThat(it).containsEntry("voBalance", "2")
            assertThat(it).containsEntry("pvoBalance", "3")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-booking-moved-to"),
          check {
            assertThat(it).containsEntry("bookingId", bookingId.toString())
            assertThat(it).containsEntry("prisonNumber", movedToNomsNumber)
            assertThat(it).containsEntry("voBalance", "null")
            assertThat(it).containsEntry("pvoBalance", "null")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithNomisFailures {

      @BeforeEach
      fun setUp() {
        nomisApi.stubCheckServicePrisonForPrisoner(prisonNumber = movedFromNomsNumber)
        visitBalanceDpsApi.stubGetVisitBalance(PrisonerBalanceDto(prisonerId = movedFromNomsNumber, voBalance = 2, pvoBalance = 3))
        visitBalanceNomisApi.stubPutVisitBalance(prisonNumber = movedFromNomsNumber, status = HttpStatus.INTERNAL_SERVER_ERROR)
        publishBookingMovedEvent(bookingId, movedFromNomsNumber, movedToNomsNumber, bookingStartDateTime)

        await untilCallTo {
          visitBalanceDlqClient!!.countAllMessagesOnQueue(visitBalanceDlqUrl!!).get()
        } matches { it == 1 }
      }

      @Test
      fun `will attempt to retrieve the balance adjustment from Dps `() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$movedFromNomsNumber/balance")))
      }

      @Test
      fun `will not create telemetry tracking`() {
        verify(telemetryClient, never()).trackEvent(any(), any(), isNull())
      }

      @Test
      fun `will attempt call to Nomis several times and keep failing`() {
        visitBalanceNomisApi.verify(2, putRequestedFor(urlPathEqualTo("/prisoners/$movedFromNomsNumber/visit-balance")))
      }
    }

    @Nested
    inner class HappyPathWithDpsFailures {
      val bookingId = 123456L

      @BeforeEach
      fun setUp() {
        nomisApi.stubCheckServicePrisonForPrisoner(prisonNumber = movedFromNomsNumber)
        visitBalanceDpsApi.stubGetVisitBalance(movedFromNomsNumber, status = HttpStatus.INTERNAL_SERVER_ERROR)
        publishBookingMovedEvent(bookingId, movedFromNomsNumber, movedToNomsNumber, bookingStartDateTime)
        await untilCallTo {
          visitBalanceDlqClient!!.countAllMessagesOnQueue(visitBalanceDlqUrl!!).get()
        } matches { it == 1 }
      }

      @Test
      fun `will attempt to retrieve visit balance adjustment from Dps`() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$movedFromNomsNumber/balance")))
      }

      @Test
      fun `will not create telemetry tracking`() {
        verify(telemetryClient, never()).trackEvent(any(), any(), isNull())
      }

      @Test
      fun `will not attempt call to Nomis `() {
        visitBalanceNomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/$movedFromNomsNumber/visit-balance")))
        visitBalanceNomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/$movedToNomsNumber/visit-balance")))
      }
    }
  }

  private fun sendVisitBalanceAdjustmentToQueue(
    eventType: String = "prison-visit-allocation.adjustment.created",
    offenderNo: String = "A1234KT",
    visitBalanceAdjustmentId: String = "a77fa39f-49cf-4e07-af09-f47cfdb3c6ef",
  ) {
    visitBalanceQueueClient.sendMessage(
      SendMessageRequest.builder().queueUrl(visitBalanceQueueUrl).messageBody(
        visitBalanceAdjustmentMessagePayload(
          eventType = eventType,
          offenderNo = offenderNo,
          visitBalanceAdjustmentId = visitBalanceAdjustmentId,
        ),
      )
        .build(),
    ).get()
  }

  private fun visitBalanceAdjustmentMessagePayload(
    eventType: String,
    offenderNo: String,
    visitBalanceAdjustmentId: String,
  ) = // language=JSON
    """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"additionalInformation\":{\"adjustmentId\":\"$visitBalanceAdjustmentId\"},\"personReference\":{\"identifiers\":[{\"type\":\"NOMIS\",\"value\":\"$offenderNo\"}]}}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
    """.trimIndent()

  private fun publishBookingMovedEvent(bookingId: Long, old: String, new: String, bookingStartDateTime: String) {
    visitBalanceQueueClient.sendMessage(
      SendMessageRequest.builder().queueUrl(visitBalanceQueueUrl).messageBody(
        bookingMovedDomainEvent(bookingId = bookingId, movedFromNomsNumber = old, movedToNomsNumber = new, bookingStartDateTime = bookingStartDateTime),
      )
        .eventTypeMessageAttributes("prison-offender-events.prisoner.booking.moved").build(),
    ).get()
  }

  fun bookingMovedDomainEvent(
    eventType: String = "prison-offender-events.prisoner.booking.moved",
    bookingId: Long = 1234567,
    movedToNomsNumber: String = "A1234KT",
    movedFromNomsNumber: String = "A1000KT",
    bookingStartDateTime: String = "2019-10-21T15:00:25.489964",
  ) = //language=JSON
    """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"movedToNomsNumber\":\"$movedToNomsNumber\", \"movedFromNomsNumber\":\"$movedFromNomsNumber\", \"bookingId\":\"$bookingId\", \"bookingStartDateTime\":\"$bookingStartDateTime\"}}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
    """.trimIndent()
}
