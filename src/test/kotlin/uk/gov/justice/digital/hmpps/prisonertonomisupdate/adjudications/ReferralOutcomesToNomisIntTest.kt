package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

private const val CHARGE_NUMBER = "12345-1"
private const val ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"
private const val CHARGE_SEQUENCE = 1

class ReferralOutcomesToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateReferral {
    @Nested
    inner class WhenPoliceReferral {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithPoliceReferral(
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubReferralUpsert(ADJUDICATION_NUMBER)
        publishCreatePoliceReferralDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQUENCE/result")),
        )
        verifyReferralCreatedSuccessCustomEvent(findingCode = "REF_POLICE", plea = "NOT_ASKED")
      }
    }

    @Nested
    inner class WhenNotProceedReferral {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithNotProceedReferral(
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubReferralUpsert(ADJUDICATION_NUMBER, finding = "NOT_PROCEED")
        publishCreateNotProceedReferralDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQUENCE/result")),
        )
        verifyReferralCreatedSuccessCustomEvent(findingCode = "NOT_PROCEED", plea = "NOT_ASKED")
      }
    }

    private fun verifyReferralCreatedSuccessCustomEvent(
      findingCode: String = "CHARGE_PROVEN",
      plea: String = "NOT_GUILTY",
    ) {
      verify(telemetryClient).trackEvent(
        eq("adjudication-referral-created-success"),
        org.mockito.kotlin.check {
          Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
          Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
          Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
          Assertions.assertThat(it["findingCode"]).isEqualTo(findingCode)
          Assertions.assertThat(it["plea"]).isEqualTo(plea)
        },
        isNull(),
      )
    }

    private fun waitForProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class DeleteReferral {
    @Nested
    inner class WhenReferralHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGet(
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubReferralDelete(ADJUDICATION_NUMBER, CHARGE_SEQUENCE)
        publishDeleteReferralDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("hearing-result-deleted-success"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to delete the hearing result`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQUENCE/result")))
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class DeleteHearingReferralOutcome {
    @Nested
    inner class WhenReferralResultHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithPoliceReferral(
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubReferralUpsert(ADJUDICATION_NUMBER, CHARGE_SEQUENCE)
        publishDeleteReferralOutcomeDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("hearing-result-deleted-success"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to update the referral outcome (rolling back to referral state)`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.findingCode", WireMock.equalTo("REF_POLICE"))),
        )
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  private fun publishCreatePoliceReferralDomainEvent() {
    val eventType = "adjudication.outcome.referPolice"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(referralMessagePayload(CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishCreateNotProceedReferralDomainEvent() {
    val eventType = "adjudication.outcome.notProceed"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(referralMessagePayload(CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeleteReferralDomainEvent() {
    val eventType = "adjudication.referral.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(referralMessagePayload(CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeleteReferralOutcomeDomainEvent() {
    val eventType = "adjudication.referral.outcome.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(referralMessagePayload(CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun referralMessagePayload(
    chargeNumber: String,
    prisonId: String,
    prisonerNumber: String,
    eventType: String,
    status: String = "REFER_POLICE",
  ) =
    """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId", "prisonerNumber": "$prisonerNumber", "status": "$status"}}"""
}
