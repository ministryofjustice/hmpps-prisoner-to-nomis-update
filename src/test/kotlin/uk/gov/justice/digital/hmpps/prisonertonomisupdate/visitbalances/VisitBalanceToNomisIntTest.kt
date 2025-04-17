package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceDpsApiExtension.Companion.visitBalanceDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

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
        publishVisitBalanceAdjustmentDomainEvent()
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
            .withRequestBodyJsonPath("adjustmentReasonCode", "GOV")
            .withRequestBodyJsonPath("adjustmentDate", "2021-01-18")
            .withRequestBodyJsonPath("previousVisitOrderCount", 12)
            .withRequestBodyJsonPath("visitOrderChange", 2)
            .withRequestBodyJsonPath("previousPrivilegedVisitOrderCount", 7)
            .withRequestBodyJsonPath("privilegedVisitOrderChange", -1)
            .withRequestBodyJsonPath("comment", "A comment")
            .withRequestBodyJsonPath("expiryBalance", 6)
            .withRequestBodyJsonPath("expiryDate", "2021-02-19")
            .withRequestBodyJsonPath("endorsedStaffId", 123)
            .withRequestBodyJsonPath("authorisedStaffId", 345),
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
    inner class HappyPathWithNomisFailures {

      @BeforeEach
      fun setUp() {
        visitBalanceDpsApi.stubGetVisitBalanceAdjustment(visitBalanceAdjId)
        visitBalanceNomisApi.stubPostVisitBalanceAdjustment(status = HttpStatus.INTERNAL_SERVER_ERROR)
        publishVisitBalanceAdjustmentDomainEvent()
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
        verify(telemetryClient, Times(0)).trackEvent(any(), any(), isNull())
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
        publishVisitBalanceAdjustmentDomainEvent()
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
        verify(telemetryClient, Times(0)).trackEvent(any(), any(), isNull())
      }

      @Test
      fun `will not attempt call to Nomis `() {
        visitBalanceNomisApi.verify(0, postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/visit-balance-adjustments")))
      }
    }
  }

  @Nested
  @DisplayName("prison-visit-allocation.balance.updated")
  inner class VisitBalanceUpdated {

    val offenderNo = "A1234KT"

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setup() {
        visitBalanceDpsApi.stubGetVisitBalance()
        visitBalanceNomisApi.stubPutVisitBalance()
        publishVisitBalanceDomainEvent()
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the balance details from Dps`() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$offenderNo/balance")))
      }

      @Test
      fun `will update the balance in Nomis`() {
        visitBalanceNomisApi.verify(
          putRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/visit-balance"))
            .withRequestBodyJsonPath("remainingVisitOrders", 24)
            .withRequestBodyJsonPath("remainingPrivilegedVisitOrders", 3),
        )
      }

      @Test
      fun `will send telemetry event showing the update success`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-balance-synchronisation-updated-success"),
          check {
            assertThat(it).containsEntry("prisonNumber", "A1234KT")
            assertThat(it).containsEntry("visitBalance", "24")
            assertThat(it).containsEntry("privilegedVisitBalance", "3")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithNomisFailures {

      @BeforeEach
      fun setUp() {
        visitBalanceDpsApi.stubGetVisitBalance()
        visitBalanceNomisApi.stubPutVisitBalance(status = HttpStatus.INTERNAL_SERVER_ERROR)
        publishVisitBalanceDomainEvent()
        await untilCallTo {
          visitBalanceDlqClient!!.countAllMessagesOnQueue(visitBalanceDlqUrl!!).get()
        } matches { it == 1 }
      }

      @Test
      fun `will attempt to retrieve the balance from Dps `() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$offenderNo/balance")))
      }

      @Test
      fun `will not create telemetry tracking`() {
        verify(telemetryClient, Times(0)).trackEvent(any(), any(), isNull())
      }

      @Test
      fun `will attempt call to Nomis several times and keep failing`() {
        visitBalanceNomisApi.verify(2, putRequestedFor(urlPathEqualTo("/prisoners/A1234KT/visit-balance")))
      }
    }

    @Nested
    inner class HappyPathWithDpsFailures {
      val bookingId = 123456L

      @BeforeEach
      fun setUp() {
        visitBalanceDpsApi.stubGetVisitBalance(status = HttpStatus.INTERNAL_SERVER_ERROR)
        publishVisitBalanceDomainEvent()
        await untilCallTo {
          visitBalanceDlqClient!!.countAllMessagesOnQueue(visitBalanceDlqUrl!!).get()
        } matches { it == 1 }
      }

      @Test
      fun `will attempt to retrieve visit balance from Dps`() {
        visitBalanceDpsApi.verify(getRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/$offenderNo/balance")))
      }

      @Test
      fun `will not create telemetry tracking`() {
        verify(telemetryClient, Times(0)).trackEvent(any(), any(), isNull())
      }

      @Test
      fun `will not attempt call to Nomis `() {
        visitBalanceNomisApi.verify(0, putRequestedFor(urlPathEqualTo("/prisoners/A1234KT/visit-balance")))
      }
    }
  }

  private fun publishVisitBalanceAdjustmentDomainEvent(
    eventType: String = "prison-visit-allocation.adjustment.created",
    offenderNo: String = "A1234KT",
    visitBalanceAdjustmentId: String = "a77fa39f-49cf-4e07-af09-f47cfdb3c6ef",
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          visitBalanceAdjustmentMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
            visitBalanceAdjustmentId = visitBalanceAdjustmentId,
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishVisitBalanceDomainEvent(
    eventType: String = "prison-visit-allocation.balance.updated",
    offenderNo: String = "A1234KT",
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          visitBalanceMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}

fun visitBalanceAdjustmentMessagePayload(
  eventType: String,
  offenderNo: String,
  visitBalanceAdjustmentId: String,
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "detailUrl":"https://somecallback", 
      "additionalInformation": {
        "visitBalanceAdjustmentUuid": "$visitBalanceAdjustmentId"
      },
      "personReference": {
        "identifiers": [
          {
            "type" : "NOMS", "value": "$offenderNo"
          }
        ]
      }
    }
    """

fun visitBalanceMessagePayload(
  eventType: String,
  offenderNo: String,
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "detailUrl":"https://somecallback",
      "personReference": {
        "identifiers": [
          {
            "type" : "NOMS", "value": "$offenderNo"
          }
        ]
      }
    }
    """
