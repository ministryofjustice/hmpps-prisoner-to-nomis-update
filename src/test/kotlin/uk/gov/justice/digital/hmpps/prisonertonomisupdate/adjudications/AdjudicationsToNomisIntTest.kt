package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedDamageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedEvidenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.util.UUID

private const val CHARGE_NUMBER_FOR_CREATION = "12345"
private const val CHARGE_NUMBER_FOR_UPDATE = "12345-1"
private const val CHARGE_SEQ = 1
private const val ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"
private const val INTERNAL_LOCATION_DPS_ID = "be1ee367-8cfa-4499-942b-3938d375f41e"
private const val INTERNAL_LOCATION_NOMIS_ID = 197683L
internal val locationMappingResponse = """
    {
      "dpsLocationId": "$INTERNAL_LOCATION_DPS_ID",
      "nomisLocationId": $INTERNAL_LOCATION_NOMIS_ID,
      "mappingType": "LOCATION_CREATED"
    }
""".trimIndent()

class AdjudicationsToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateAdjudication {
    @Nested
    inner class WhenChargeHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          incidentLocationUuid = UUID.fromString(INTERNAL_LOCATION_DPS_ID),
        )
        nomisApi.stubAdjudicationCreate(OFFENDER_NO, ADJUDICATION_NUMBER, CHARGE_SEQ)
        mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
        mappingServer.stubCreateAdjudication()
        mappingServer.stubGetMappingGivenDpsLocationId(INTERNAL_LOCATION_DPS_ID, locationMappingResponse)
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
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/adjudications"))
              .withRequestBody(
                matchingJsonPath(
                  "adjudicationNumber",
                  equalTo(ADJUDICATION_NUMBER.toString()),
                ),
              )
              .withRequestBody(matchingJsonPath("chargeSequence", equalTo(CHARGE_SEQ.toString())))
              .withRequestBody(matchingJsonPath("chargeNumber", equalTo(CHARGE_NUMBER_FOR_CREATION))),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenLocationMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          incidentLocationUuid = UUID.fromString(INTERNAL_LOCATION_DPS_ID),
        )
        mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
        mappingServer.stubGetMappingGivenDpsLocationIdWithError(INTERNAL_LOCATION_DPS_ID)
        publishCreateAdjudicationDomainEvent()
      }

      @Test
      fun `will put the failed message onto the dlq`() {
        await untilCallTo { awsSqsAdjudicationDlqClient!!.countMessagesOnQueue(adjudicationDlqUrl!!).get() } matches { it == 1 }
      }

      @Test
      fun `will create failed telemetry`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("adjudication-create-failed"),
            check {
              assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will not call nomis api to update the adjudication`() {
        await untilCallTo {
          awsSqsAdjudicationDlqClient!!.countMessagesOnQueue(adjudicationDlqUrl!!).get()
        } matches { it == 1 }

        nomisApi.verify(
          0,
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/adjudications")),
        )
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForAdjudication {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
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
        mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
        mappingServer.stubCreateAdjudicationWithErrorFollowedBySlowSuccess()
        mappingServer.stubGetMappingGivenDpsLocationId(INTERNAL_LOCATION_DPS_ID, locationMappingResponse)
        nomisApi.stubAdjudicationCreate(OFFENDER_NO, adjudicationNumber = 12345)
        adjudicationsApiServer.stubChargeGet(chargeNumber = CHARGE_NUMBER_FOR_CREATION, offenderNo = OFFENDER_NO, incidentLocationUuid = UUID.fromString(INTERNAL_LOCATION_DPS_ID))
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
          mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/adjudications"))
              .withRequestBody(
                matchingJsonPath("chargeNumber", equalTo(CHARGE_NUMBER_FOR_CREATION)),
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
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_UPDATE, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_UPDATE,
          offenderNo = OFFENDER_NO,
          damages = listOf(
            ReportedDamageDto(
              code = ReportedDamageDto.Code.ELECTRICAL_REPAIR,
              details = "light switch",
              reporter = "QT1234T",
            ),
            ReportedDamageDto(
              code = ReportedDamageDto.Code.LOCK_REPAIR,
              details = "lock broken",
              reporter = "QT1234T",
            ),
            ReportedDamageDto(
              code = ReportedDamageDto.Code.CLEANING,
              details = "dirty carpets",
              reporter = "QT1234T",
            ),
          ),
        )

        nomisApi.stubAdjudicationRepairsUpdate(ADJUDICATION_NUMBER)

        publishUpdateAdjudicationDamagesDomainEvent(chargeNumber = CHARGE_NUMBER_FOR_UPDATE)
      }

      @Test
      fun `will retrieve the damages from the adjudication service`() {
        await untilAsserted {
          adjudicationsApiServer.verify(
            getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_UPDATE/v2")),
          )
        }
      }

      @Test
      fun `will update NOMIS with the repairs`() {
        await untilAsserted {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/repairs")),
          )
        }
      }

      @Test
      fun `will transform DPS damages to NOMIS repairs`() {
        await untilAsserted {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBody(matchingJsonPath("repairs[0].typeCode", equalTo("ELEC")))
              .withRequestBody(matchingJsonPath("repairs[0].comment", equalTo("light switch")))
              .withRequestBody(matchingJsonPath("repairs[1].typeCode", equalTo("LOCK")))
              .withRequestBody(matchingJsonPath("repairs[1].comment", equalTo("lock broken")))
              .withRequestBody(matchingJsonPath("repairs[2].typeCode", equalTo("CLEA")))
              .withRequestBody(matchingJsonPath("repairs[2].comment", equalTo("dirty carpets"))),
          )
        }
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
            assertThat(it["repairCount"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class ErrorScenarios {
      @Nested
      inner class WhenAdjudicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_UPDATE, 404)
          publishUpdateAdjudicationDamagesDomainEvent(chargeNumber = CHARGE_NUMBER_FOR_UPDATE)
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
            verify(telemetryClient, times(3)).trackEvent(
              eq("adjudication-damages-updated-failed"),
              check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenDpsChargeNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_UPDATE, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGetWithError(CHARGE_NUMBER_FOR_UPDATE, 404)
          publishUpdateAdjudicationDamagesDomainEvent(chargeNumber = CHARGE_NUMBER_FOR_UPDATE)
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
            verify(telemetryClient, times(3)).trackEvent(
              eq("adjudication-damages-updated-failed"),
              check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisAdjudicationNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_UPDATE, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(CHARGE_NUMBER_FOR_UPDATE, offenderNo = OFFENDER_NO)
          nomisApi.stubAdjudicationRepairsUpdateWithError(ADJUDICATION_NUMBER, 404)

          publishUpdateAdjudicationDamagesDomainEvent(chargeNumber = CHARGE_NUMBER_FOR_UPDATE)
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
            verify(telemetryClient, times(3)).trackEvent(
              eq("adjudication-damages-updated-failed"),
              check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }
    }

    private fun waitForUpdateAdjudicationDamagesProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class UpdateAdjudicationEvidence {
    @Nested
    inner class WhenAdjudicationMappingFound {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_UPDATE, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_UPDATE,
          offenderNo = OFFENDER_NO,
          evidence = listOf(
            ReportedEvidenceDto(
              code = ReportedEvidenceDto.Code.BAGGED_AND_TAGGED,
              identifier = "24242",
              details = "drugs",
              reporter = "AMARKE_GEN",
            ),
            ReportedEvidenceDto(
              code = ReportedEvidenceDto.Code.CCTV,
              identifier = null,
              details = "Image of fight",
              reporter = "AMARKE_GEN",
            ),
          ),
        )

        nomisApi.stubAdjudicationEvidenceUpdate(ADJUDICATION_NUMBER)

        publishUpdateAdjudicationEvidenceDomainEvent(chargeNumber = CHARGE_NUMBER_FOR_UPDATE)
      }

      @Test
      fun `will retrieve the evidence data from the adjudication service`() {
        await untilAsserted {
          adjudicationsApiServer.verify(
            getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_UPDATE/v2")),
          )
        }
      }

      @Test
      fun `will update NOMIS with the evidence`() {
        await untilAsserted {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/evidence")),
          )
        }
      }

      @Test
      fun `will transform DPS evidence to NOMIS evidence`() {
        await untilAsserted {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBody(matchingJsonPath("evidence[0].typeCode", equalTo("EVI_BAG")))
              .withRequestBody(matchingJsonPath("evidence[0].detail", equalTo("drugs")))
              .withRequestBody(matchingJsonPath("evidence[1].typeCode", equalTo("OTHER")))
              .withRequestBody(matchingJsonPath("evidence[1].detail", equalTo("Image of fight"))),
          )
        }
      }

      @Test
      fun `will create success telemetry`() {
        waitForUpdateAdjudicationEvidenceProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("adjudication-evidence-updated-success"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["evidenceCount"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class ErrorScenarios {
      @Nested
      inner class WhenAdjudicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_UPDATE, 404)
          publishUpdateAdjudicationEvidenceDomainEvent(chargeNumber = CHARGE_NUMBER_FOR_UPDATE)
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
            verify(telemetryClient, times(3)).trackEvent(
              eq("adjudication-evidence-updated-failed"),
              check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenDpsChargeNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_UPDATE, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGetWithError(CHARGE_NUMBER_FOR_UPDATE, 404)
          publishUpdateAdjudicationEvidenceDomainEvent(chargeNumber = CHARGE_NUMBER_FOR_UPDATE)
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
            verify(telemetryClient, times(3)).trackEvent(
              eq("adjudication-evidence-updated-failed"),
              check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisAdjudicationNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_UPDATE, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(CHARGE_NUMBER_FOR_UPDATE, offenderNo = OFFENDER_NO)
          nomisApi.stubAdjudicationEvidenceUpdateWithError(ADJUDICATION_NUMBER, 404)

          publishUpdateAdjudicationEvidenceDomainEvent(chargeNumber = CHARGE_NUMBER_FOR_UPDATE)
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
            verify(telemetryClient, times(3)).trackEvent(
              eq("adjudication-evidence-updated-failed"),
              check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_UPDATE)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }
    }

    private fun waitForUpdateAdjudicationEvidenceProcessingToBeComplete() {
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

  private fun publishUpdateAdjudicationDamagesDomainEvent(chargeNumber: String = CHARGE_NUMBER_FOR_UPDATE) {
    val eventType = "adjudication.damages.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationMessagePayload(chargeNumber, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishUpdateAdjudicationEvidenceDomainEvent(chargeNumber: String = CHARGE_NUMBER_FOR_UPDATE) {
    val eventType = "adjudication.evidence.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationMessagePayload(chargeNumber, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun adjudicationMessagePayload(chargeNumber: String, prisonId: String, prisonerNumber: String, eventType: String) = """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId", "prisonerNumber": "$prisonerNumber"}}"""
}
