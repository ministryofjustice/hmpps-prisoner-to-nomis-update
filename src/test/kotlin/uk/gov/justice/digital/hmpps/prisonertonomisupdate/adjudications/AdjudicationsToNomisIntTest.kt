package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

private const val CHARGE_NUMBER = "12345"
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"

class AdjudicationsToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateAdjudication {
    @Nested
    inner class WhenChargeHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        adjudicationsApiServer.stubChargeGet(CHARGE_NUMBER, offenderNo = OFFENDER_NO)
        nomisApi.stubAdjudicationCreate(OFFENDER_NO)
        publishCreateAdjudicationDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        waitForCreateAdjudicationProcessingToBeComplete()

        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForCreateAdjudicationProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("adjudication-create-success"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the adjudication`() {
        waitForCreateAdjudicationProcessingToBeComplete()

        nomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/adjudications")))
      }
    }

    private fun waitForCreateAdjudicationProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  private fun publishCreateAdjudicationDomainEvent() {
    val eventType = "adjudication.report.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationMessagePayload(CHARGE_NUMBER, PRISON_ID, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun adjudicationMessagePayload(chargeNumber: String, prisonId: String, eventType: String) =
    """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId"}}"""
}
