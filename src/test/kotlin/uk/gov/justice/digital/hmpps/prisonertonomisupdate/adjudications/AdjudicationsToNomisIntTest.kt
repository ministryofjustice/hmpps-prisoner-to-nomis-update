package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

private const val CHARGE_NUMBER_FOR_CREATION = "12345"
private const val CHARGE_NUMBER_FOR_UPDATE = "12345-1"
private const val CHARGE_SEQ = 1
private const val ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"

class AdjudicationsToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateAdjudication {
    @Nested
    inner class WhenChargeHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        adjudicationsApiServer.stubChargeGet(CHARGE_NUMBER_FOR_CREATION, offenderNo = OFFENDER_NO)
        nomisApi.stubAdjudicationCreate(OFFENDER_NO, ADJUDICATION_NUMBER, CHARGE_SEQ)
        MappingExtension.mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
        MappingExtension.mappingServer.stubCreateAdjudication()
        publishCreateAdjudicationDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        waitForCreateAdjudicationProcessingToBeComplete()

        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForCreateAdjudicationProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("adjudication-create-success"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
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

      @Test
      fun `will create a mapping between the two adjudications`() {
        waitForCreateAdjudicationProcessingToBeComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/adjudications"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "adjudicationNumber",
                  WireMock.equalTo(ADJUDICATION_NUMBER.toString()),
                ),
              )
              .withRequestBody(WireMock.matchingJsonPath("chargeSequence", WireMock.equalTo(CHARGE_SEQ.toString())))
              .withRequestBody(WireMock.matchingJsonPath("chargeNumber", WireMock.equalTo(CHARGE_NUMBER_FOR_CREATION))),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForAdjudication {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION)
        publishCreateAdjudicationDomainEvent()
      }

      @Test
      fun `will not create an adjudication in NOMIS`() {
        waitForCreateAdjudicationProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("adjudication-create-duplicate"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
          },
          isNull(),
        )

        nomisApi.verify(
          0,
          postRequestedFor(urlEqualTo("/prisoners/booking-id/$OFFENDER_NO/adjudications")),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
        MappingExtension.mappingServer.stubCreateAdjudicationWithErrorFollowedBySlowSuccess()
        nomisApi.stubAdjudicationCreate(OFFENDER_NO, adjudicationNumber = 12345)
        adjudicationsApiServer.stubChargeGet(chargeNumber = CHARGE_NUMBER_FOR_CREATION, offenderNo = OFFENDER_NO)
        publishCreateAdjudicationDomainEvent()

        await untilCallTo { adjudicationsApiServer.getCountFor("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2") } matches { it == 1 }
        await untilCallTo { nomisApi.postCountFor("/prisoners/$OFFENDER_NO/adjudications") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS adjudication once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("adjudication-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        nomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/adjudications")))
      }

      @Test
      fun `will eventually create a mapping after NOMIS adjudication is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/adjudications"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "chargeNumber",
                  WireMock.equalTo(CHARGE_NUMBER_FOR_CREATION),
                ),
              ),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("adjudication-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }

    private fun waitForCreateAdjudicationProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class UpdateAdjudicationDamages {
    @Nested
    inner class WhenAdjudicationMappingFound {
      @BeforeEach
      fun setUp() {
        publishUpdateAdjudicationDamagesDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForUpdateAdjudicationDamagesProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("adjudication-damages-updated-success"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
          },
          isNull(),
        )
      }
    }

    private fun waitForUpdateAdjudicationDamagesProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  private fun publishCreateAdjudicationDomainEvent() {
    val eventType = "adjudication.report.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationMessagePayload(CHARGE_NUMBER_FOR_CREATION, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishUpdateAdjudicationDamagesDomainEvent() {
    val eventType = "adjudication.damages.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationMessagePayload(CHARGE_NUMBER_FOR_UPDATE, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun adjudicationMessagePayload(chargeNumber: String, prisonId: String, prisonerNumber: String, eventType: String) =
    """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId", "prisonerNumber": "$prisonerNumber"}}"""
}
