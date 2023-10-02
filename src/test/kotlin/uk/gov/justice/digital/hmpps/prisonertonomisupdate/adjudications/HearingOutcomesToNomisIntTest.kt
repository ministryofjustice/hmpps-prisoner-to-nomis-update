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
import org.mockito.kotlin.times
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
private const val DPS_HEARING_ID = "345"
private const val NOMIS_HEARING_ID = 2345L
private const val CHARGE_SEQUENCE = 1

class HearingOutcomesToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateHearingCompleted {
    @Nested
    inner class WhenHearingResultHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGet(CHARGE_NUMBER, offenderNo = OFFENDER_NO)
        NomisApiExtension.nomisApi.stubHearingResultCreate(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingCompletedDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result")))

        verify(telemetryClient).trackEvent(
          eq("hearing-result-created-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
            Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
            Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHearingMappingNotFound {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingIdWithError(hearingId = DPS_HEARING_ID, 404)
        publishCreateHearingCompletedDomainEvent()
      }

      @Test
      fun `will not create a hearing result in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("hearing-result-created-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
              Assertions.assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQUENCE.toString())
            },
            isNull(),
          )

          NomisApiExtension.nomisApi.verify(
            0,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings")),
          )
        }
      }
    }

    private fun waitForCreateHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class DeleteHearingCompleted {
    @Nested
    inner class WhenHearingResultHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        NomisApiExtension.nomisApi.stubHearingResultDelete(ADJUDICATION_NUMBER, NOMIS_HEARING_ID, CHARGE_SEQUENCE)
        publishDeleteHearingCompletedDomainEvent()
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
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to delete the hearing result`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForHearing {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingIdWithError(hearingId = DPS_HEARING_ID, 404)
        publishDeleteHearingCompletedDomainEvent()
      }

      @Test
      fun `will not attempt to delete a hearing in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("hearing-result-deleted-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.deleteRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  private fun publishCreateHearingCompletedDomainEvent() {
    val eventType = "adjudication.hearingCompleted.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeleteHearingCompletedDomainEvent() {
    val eventType = "adjudication.hearingCompleted.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun hearingMessagePayload(
    hearingId: String,
    chargeNumber: String,
    prisonId: String,
    prisonerNumber: String,
    eventType: String,
  ) =
    """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId", "hearingId": "$hearingId", "prisonerNumber": "$prisonerNumber"}}"""
}
