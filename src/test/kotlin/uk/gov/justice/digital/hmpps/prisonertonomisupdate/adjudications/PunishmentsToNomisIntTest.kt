package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val CHARGE_NUMBER_FOR_CREATION = "12345-1"
private const val CHARGE_SEQ = 1
private const val ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"

class PunishmentsToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreatePunishments {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          punishments =
          // language=json
          """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18"
                }
            }
        ]
          """.trimIndent(),
        )

        nomisApi.stubAdjudicationAwardsCreate(ADJUDICATION_NUMBER, CHARGE_SEQ, awardIds = listOf(12345L to 10, 12345L to 11))
        mappingServer.stubCreatePunishments()
        publishCreatePunishmentsDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        waitForCreatePunishmentProcessingToBeComplete()

        adjudicationsApiServer.verify(WireMock.getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForCreatePunishmentProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("punishment-create-success"),
          org.mockito.kotlin.check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
            assertThat(it["punishmentsCount"]).isEqualTo("2")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the awards`() {
        waitForCreatePunishmentProcessingToBeComplete()

        nomisApi.verify(postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")))
      }

      @Test
      fun `will map DPS punishments to NOMIS awards`() {
        waitForCreatePunishmentProcessingToBeComplete()

        nomisApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBody(matchingJsonPath("awardRequests[0].sanctionType", equalTo("CC")))
            .withRequestBody(matchingJsonPath("awardRequests[0].sanctionStatus", equalTo("IMMEDIATE")))
            .withRequestBody(matchingJsonPath("awardRequests[0].effectiveDate", equalTo("2023-10-04")))
            .withRequestBody(matchingJsonPath("awardRequests[0].sanctionDays", equalTo("3")))
            .withRequestBody(matchingJsonPath("awardRequests[1].sanctionType", equalTo("EXTW")))
            .withRequestBody(matchingJsonPath("awardRequests[1].sanctionStatus", equalTo("SUSPENDED")))
            .withRequestBody(matchingJsonPath("awardRequests[1].effectiveDate", equalTo("2023-10-18")))
            .withRequestBody(matchingJsonPath("awardRequests[1].sanctionDays", equalTo("12"))),
        )
      }

      @Test
      fun `will create a mapping between the set of punishments and awards`() {
        waitForCreatePunishmentProcessingToBeComplete()

        await untilAsserted {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/punishments"))
              .withRequestBody(matchingJsonPath("punishments[0].nomisBookingId", equalTo("12345")))
              .withRequestBody(matchingJsonPath("punishments[0].nomisSanctionSequence", equalTo("10")))
              .withRequestBody(matchingJsonPath("punishments[0].dpsPunishmentId", equalTo("634")))
              .withRequestBody(matchingJsonPath("punishments[1].nomisBookingId", equalTo("12345")))
              .withRequestBody(matchingJsonPath("punishments[1].nomisSanctionSequence", equalTo("11")))
              .withRequestBody(matchingJsonPath("punishments[1].dpsPunishmentId", equalTo("667"))),
          )
        }
      }
    }

    @Nested
    inner class ErrorScenarios {
      @Nested
      inner class WhenAdjudicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
          publishCreatePunishmentsDomainEvent()
        }

        @Test
        fun `an error will lead to message being added to DLQ`() {
          await untilCallTo {
            awsSqsAdjudicationDlqClient!!.countAllMessagesOnQueue(adjudicationDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will track failure telemetry for each retry`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.times(3)).trackEvent(
              Mockito.eq("punishment-create-failed"),
              org.mockito.kotlin.check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            punishments =
            // language=json
            """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18"
                }
            }
        ]
            """.trimIndent(),
          )

          nomisApi.stubAdjudicationAwardsCreate(ADJUDICATION_NUMBER, CHARGE_SEQ, awardIds = listOf(12345L to 10, 12345L to 11))
          mappingServer.stubCreatePunishmentsWithErrorFollowedBySuccess()
          publishCreatePunishmentsDomainEvent()

          mappingServer.stubCreateAdjudicationWithErrorFollowedBySlowSuccess()
        }

        @Test
        fun `should only create the NOMIS punishments once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-create-mapping-retry-success"),
              any(),
              isNull(),
            )
          }

          nomisApi.verify(1, postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")))
        }

        @Test
        fun `will eventually create a mapping after NOMIS adjudication is created`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              postRequestedFor(urlEqualTo("/mapping/punishments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-create-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
        }
      }
    }

    private fun waitForCreatePunishmentProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  private fun publishCreatePunishmentsDomainEvent() {
    val eventType = "adjudication.punishments.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationPunishmentMessagePayload(CHARGE_NUMBER_FOR_CREATION, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun adjudicationPunishmentMessagePayload(chargeNumber: String, prisonId: String, prisonerNumber: String, eventType: String) =
    // language=json
    """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId", "prisonerNumber": "$prisonerNumber", "status": "CHARGE_PROVED"}}"""
}
