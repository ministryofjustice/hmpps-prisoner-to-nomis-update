package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
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

private const val CHARGE_NUMBER_FOR_CREATION = "12345"
private const val CHARGE_NUMBER_FOR_UPDATE = "12345-1"
private const val ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"
private const val DPS_HEARING_ID = "345"
private const val NOMIS_HEARING_ID = 2345L

class HearingsToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateHearing {
    @Nested
    inner class WhenHearingHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGet(CHARGE_NUMBER_FOR_CREATION, offenderNo = OFFENDER_NO)
        NomisApiExtension.nomisApi.stubHearingCreate(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingIdWithError(DPS_HEARING_ID, 404)
        MappingExtension.mappingServer.stubCreateHearing()
        publishCreateHearingDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForCreateHearingProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("hearing-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
            Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
            Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the hearing`() {
        waitForCreateHearingProcessingToBeComplete()

        NomisApiExtension.nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings")))
      }

      @Test
      fun `will create a mapping between the two hearings`() {
        waitForCreateHearingProcessingToBeComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/hearings"))
              .withRequestBody(WireMock.matchingJsonPath("dpsHearingId", WireMock.equalTo(DPS_HEARING_ID)))
              .withRequestBody(WireMock.matchingJsonPath("nomisHearingId", WireMock.equalTo(NOMIS_HEARING_ID.toString()))),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForHearing {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingId(dpsHearingId = DPS_HEARING_ID, nomisHearingId = NOMIS_HEARING_ID)
        publishCreateHearingDomainEvent()
      }

      @Test
      fun `will not create an hearing in NOMIS`() {
        waitForCreateHearingProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("hearing-create-duplicate"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
            Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings")),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubCreateHearingWithErrorFollowedBySlowSuccess()
        NomisApiExtension.nomisApi.stubHearingCreate(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGet(chargeNumber = CHARGE_NUMBER_FOR_CREATION, offenderNo = OFFENDER_NO)
        publishCreateHearingDomainEvent()

        await untilCallTo { AdjudicationsApiExtension.adjudicationsApiServer.getCountFor("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2") } matches { it == 1 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS hearing once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("hearing-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS hearing is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/hearings"))
              .withRequestBody(
                WireMock.matchingJsonPath("dpsHearingId", WireMock.equalTo(DPS_HEARING_ID)),
              ),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("hearing-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }

    private fun waitForCreateHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class UpdateHearing {
    @Nested
    inner class WhenHearingHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_UPDATE, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGet(CHARGE_NUMBER_FOR_UPDATE, offenderNo = OFFENDER_NO)
        NomisApiExtension.nomisApi.stubHearingUpdate(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishUpdateHearingDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        waitForHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_UPDATE/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForHearingProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("hearing-updated-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
            Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
            Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the hearing`() {
        waitForHearingProcessingToBeComplete()

        NomisApiExtension.nomisApi.verify(WireMock.putRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForHearing {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_UPDATE, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingIdWithError(hearingId = DPS_HEARING_ID, 404)
        publishUpdateHearingDomainEvent()
      }

      @Test
      fun `will not create an hearing in NOMIS`() {
        waitForHearingProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("hearing-updated-failed"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
            Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID")),
        )
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  private fun publishCreateHearingDomainEvent() {
    val eventType = "adjudication.hearing.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER_FOR_CREATION, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishUpdateHearingDomainEvent() {
    val eventType = "adjudication.hearing.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER_FOR_UPDATE, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun hearingMessagePayload(hearingId: String, chargeNumber: String, prisonId: String, prisonerNumber: String, eventType: String) =
    """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId", "hearingId": "$hearingId", "prisonerNumber": "$prisonerNumber"}}"""
}
