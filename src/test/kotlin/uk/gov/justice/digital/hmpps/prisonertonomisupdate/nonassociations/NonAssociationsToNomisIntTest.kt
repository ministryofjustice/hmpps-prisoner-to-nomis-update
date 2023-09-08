package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

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
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApiServer
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val NON_ASSOCIATION_ID = 12345L
private const val OFFENDER_NO_1 = "A1234AA"
private const val OFFENDER_NO_2 = "B1234BB"

class NonAssociationsToNomisIntTest : SqsIntegrationTestBase() {

  val nonAssociationApiResponse = """
    {
      "id": $NON_ASSOCIATION_ID,
      "offenderNo": "$OFFENDER_NO_1",
      "reasonCode": "VIC",
      "reasonDescription": "Victim",
      "typeCode": "WING",
      "typeDescription": "Do Not Locate on Same Wing",
      "effectiveDate": "2021-07-05T10:55:04",
      "authorisedBy": "Officer Alice B.",
      "comments": "Mr. Bloggs assaulted Mr. Hall",
      "offenderNonAssociation": {
        "offenderNo": "$OFFENDER_NO_2",
        "reasonCode": "PER",
        "reasonDescription": "Perpetrator"
      }
    }
  """.trimIndent()

  val nonAssociationMappingResponse = """
    {
      "nonAssociationId": $NON_ASSOCIATION_ID,
      "firstOffenderNo": "$OFFENDER_NO_1",
      "secondOffenderNo": "$OFFENDER_NO_2",
      "nomisTypeSequence": 1,
      "mappingType": "NON_ASSOCIATION_CREATED"
    }
  """.trimIndent()

  @Nested
  inner class Create {
    @Nested
    inner class WhenNonAssociationHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        nonAssociationsApiServer.stubGetNonAssociation(NON_ASSOCIATION_ID, nonAssociationApiResponse)
        NomisApiExtension.nomisApi.stubNonAssociationCreate("""{ "typeSequence": 1 }""")
        MappingExtension.mappingServer.stubGetMappingGivenNonAssociationIdWithError(NON_ASSOCIATION_ID, 404)
        MappingExtension.mappingServer.stubCreateNonAssociation()
        publishNonAssociationDomainEvent("non-associations.created")
      }

      @Test
      fun `will callback back to non-association service to get more details`() {
        waitForCreateProcessingToBeComplete()

        nonAssociationsApiServer.verify(getRequestedFor(urlEqualTo("/legacy/api/non-associations/$NON_ASSOCIATION_ID")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForCreateProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("nonAssociation-create-success"),
          org.mockito.kotlin.check {
            assertThat(it["nonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
            assertThat(it["firstOffenderNo"]).isEqualTo(OFFENDER_NO_1)
            assertThat(it["secondOffenderNo"]).isEqualTo(OFFENDER_NO_2)
            assertThat(it["nomisTypeSequence"]).isEqualTo("1")
            assertThat(it["mappingType"]).isEqualTo("NON_ASSOCIATION_CREATED")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the non-association`() {
        waitForCreateProcessingToBeComplete()

        NomisApiExtension.nomisApi.verify(postRequestedFor(urlEqualTo("/non-associations")))
      }

      @Test
      fun `will create a mapping`() {
        waitForCreateProcessingToBeComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/non-associations"))
              .withRequestBody(matchingJsonPath("nonAssociationId", equalTo(NON_ASSOCIATION_ID.toString())))
              .withRequestBody(matchingJsonPath("firstOffenderNo", equalTo(OFFENDER_NO_1)))
              .withRequestBody(matchingJsonPath("secondOffenderNo", equalTo(OFFENDER_NO_2)))
              .withRequestBody(matchingJsonPath("nomisTypeSequence", equalTo("1"))),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForNonAssociation {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
        publishNonAssociationDomainEvent("non-associations.created")
      }

      @Test
      fun `will not create an non-association in NOMIS`() {
        waitForCreateProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("nonAssociation-create-duplicate"),
          org.mockito.kotlin.check {
            assertThat(it["nonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          postRequestedFor(urlEqualTo("/non-associations")),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetMappingGivenNonAssociationIdWithError(NON_ASSOCIATION_ID, 404)
        MappingExtension.mappingServer.stubCreateNonAssociationWithErrorFollowedBySlowSuccess()
        NomisApiExtension.nomisApi.stubNonAssociationCreate("""{ "typeSequence": 1 }""")
        nonAssociationsApiServer.stubGetNonAssociation(NON_ASSOCIATION_ID, nonAssociationApiResponse)
        publishNonAssociationDomainEvent("non-associations.created")

        await untilCallTo { nonAssociationsApiServer.getCountFor("/legacy/api/non-associations/$NON_ASSOCIATION_ID") } matches { it == 1 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/non-associations") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS non-association once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("non-association-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          postRequestedFor(urlEqualTo("/non-associations")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS non-association is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/non-associations"))
              .withRequestBody(matchingJsonPath("nonAssociationId", equalTo("$NON_ASSOCIATION_ID")))
              .withRequestBody(matchingJsonPath("firstOffenderNo", equalTo(OFFENDER_NO_1)))
              .withRequestBody(matchingJsonPath("secondOffenderNo", equalTo(OFFENDER_NO_2)))
              .withRequestBody(matchingJsonPath("nomisTypeSequence", equalTo("1"))),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("non-association-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }

    private fun waitForCreateProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class Update {

    @Nested
    inner class WhenNonAssociationHasJustBeenUpdatedByNonAssociationService {

      @BeforeEach
      fun setUp() {
        NonAssociationsApiExtension.nonAssociationsApiServer.stubGetNonAssociation(id = NON_ASSOCIATION_ID, response = nonAssociationApiResponse)
        MappingExtension.mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
        NomisApiExtension.nomisApi.stubNonAssociationAmend(OFFENDER_NO_1, OFFENDER_NO_2)
        publishNonAssociationDomainEvent("non-associations.amended")
      }

      @Test
      fun `will callback back to nonAssociation service to get more details`() {
        await untilAsserted {
          NonAssociationsApiExtension.nonAssociationsApiServer.verify(getRequestedFor(urlEqualTo("/legacy/api/non-associations/$NON_ASSOCIATION_ID")))
        }
      }

      @Test
      fun `will update an nonAssociation in NOMIS`() {
        await untilAsserted {
          NomisApiExtension.nomisApi.verify(
            putRequestedFor(urlEqualTo("/non-associations/offender/$OFFENDER_NO_1/ns-offender/$OFFENDER_NO_2/sequence/1"))
              .withRequestBody(matchingJsonPath("reason", equalTo("VIC")))
              .withRequestBody(matchingJsonPath("recipReason", equalTo("PER")))
              .withRequestBody(matchingJsonPath("type", equalTo("WING")))
              .withRequestBody(matchingJsonPath("effectiveDate", equalTo("2021-07-05")))
              .withRequestBody(matchingJsonPath("authorisedBy", equalTo("Officer Alice B.")))
              .withRequestBody(matchingJsonPath("comment", equalTo("Mr. Bloggs assaulted Mr. Hall"))),
          )
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("nonAssociation-amend-success"),
            org.mockito.kotlin.check {
              assertThat(it["nonAssociationId"]).isEqualTo(NON_ASSOCIATION_ID.toString())
              assertThat(it["offender1"]).isEqualTo(OFFENDER_NO_1)
              assertThat(it["offender2"]).isEqualTo(OFFENDER_NO_2)
              assertThat(it["sequence"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class Exceptions {

      @Nested
      inner class WhenServiceFailsOnce {

        @BeforeEach
        fun setUp() {
          NonAssociationsApiExtension.nonAssociationsApiServer.stubGetNonAssociationWithErrorFollowedBySlowSuccess(
            id = NON_ASSOCIATION_ID,
            response = nonAssociationApiResponse,
          )
          MappingExtension.mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
          NomisApiExtension.nomisApi.stubNonAssociationAmend(OFFENDER_NO_1, OFFENDER_NO_2)
          publishNonAssociationDomainEvent("non-associations.amended")
        }

        @Test
        fun `will callback back to nonAssociation service twice to get more details`() {
          await untilAsserted {
            NonAssociationsApiExtension.nonAssociationsApiServer.verify(2, getRequestedFor(urlEqualTo("/legacy/api/non-associations/$NON_ASSOCIATION_ID")))
            verify(telemetryClient).trackEvent(Mockito.eq("nonAssociation-amend-success"), any(), isNull())
          }
        }

        @Test
        fun `will eventually update the nonAssociation in NOMIS`() {
          await untilAsserted {
            NomisApiExtension.nomisApi.verify(1, putRequestedFor(urlEqualTo("/non-associations/offender/$OFFENDER_NO_1/ns-offender/$OFFENDER_NO_2/sequence/1")))
            verify(telemetryClient).trackEvent(Mockito.eq("nonAssociation-amend-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(Mockito.eq("nonAssociation-amend-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenServiceKeepsFailing {

        @BeforeEach
        fun setUp() {
          NonAssociationsApiExtension.nonAssociationsApiServer.stubGetNonAssociation(id = NON_ASSOCIATION_ID, response = nonAssociationApiResponse)
          MappingExtension.mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
          NomisApiExtension.nomisApi.stubNonAssociationAmendWithError(OFFENDER_NO_1, OFFENDER_NO_2, 503)
          publishNonAssociationDomainEvent("non-associations.amended")
        }

        @Test
        fun `will callback back to nonAssociation service 3 times before given up`() {
          await untilAsserted {
            NonAssociationsApiExtension.nonAssociationsApiServer.verify(3, getRequestedFor(urlEqualTo("/legacy/api/non-associations/$NON_ASSOCIATION_ID")))
          }
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              Mockito.eq("nonAssociation-amend-failed"),
              org.mockito.kotlin.check {
                assertThat(it["nonAssociationId"]).isEqualTo(NON_ASSOCIATION_ID.toString())
                assertThat(it["offender1"]).isEqualTo(OFFENDER_NO_1)
                assertThat(it["offender2"]).isEqualTo(OFFENDER_NO_2)
                assertThat(it["sequence"]).isEqualTo("1")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsNonAssociationDlqClient!!.countAllMessagesOnQueue(nonAssociationDlqUrl!!).get()
          } matches { it == 1 }
        }
      }
    }
  }

  @Nested
  inner class CloseNonAssociation {
    @Nested
    inner class WhenNonAssociationHasJustBeenClosedByNonAssociationService {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
        NomisApiExtension.nomisApi.stubNonAssociationClose(OFFENDER_NO_1, OFFENDER_NO_2)
        publishNonAssociationDomainEvent("non-associations.closed")
      }

      @Test
      fun `will close an non-association in NOMIS`() {
        await untilAsserted {
          NomisApiExtension.nomisApi.verify(putRequestedFor(urlEqualTo("/non-associations/offender/$OFFENDER_NO_1/ns-offender/$OFFENDER_NO_2/sequence/1/close")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("nonAssociation-close-success"),
            org.mockito.kotlin.check {
              assertThat(it["nonAssociationId"]).isEqualTo(NON_ASSOCIATION_ID.toString())
              assertThat(it["offender1"]).isEqualTo(OFFENDER_NO_1)
              assertThat(it["offender2"]).isEqualTo(OFFENDER_NO_2)
              assertThat(it["sequence"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class Exceptions {

      @Nested
      inner class WhenServiceFailsOnce {

        @BeforeEach
        fun setUp() {
          MappingExtension.mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
          NomisApiExtension.nomisApi.stubNonAssociationCloseWithErrorFollowedBySlowSuccess(OFFENDER_NO_1, OFFENDER_NO_2)
          publishNonAssociationDomainEvent("non-associations.closed")
        }

        @Test
        fun `will callback back to mapping service twice to get more details`() {
          await untilAsserted {
            MappingExtension.mappingServer.verify(2, getRequestedFor(urlEqualTo("/mapping/non-associations/non-association-id/$NON_ASSOCIATION_ID")))
          }
        }

        @Test
        fun `will eventually close the non-association in NOMIS`() {
          await untilAsserted {
            NomisApiExtension.nomisApi.verify(2, putRequestedFor(urlEqualTo("/non-associations/offender/$OFFENDER_NO_1/ns-offender/$OFFENDER_NO_2/sequence/1/close")))
            verify(telemetryClient).trackEvent(Mockito.eq("nonAssociation-close-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(Mockito.eq("nonAssociation-close-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          nonAssociationsApiServer.stubGetNonAssociation(id = NON_ASSOCIATION_ID, response = nonAssociationApiResponse)
          MappingExtension.mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
          NomisApiExtension.nomisApi.stubNonAssociationCloseWithError(OFFENDER_NO_1, OFFENDER_NO_2, 503)
          publishNonAssociationDomainEvent("non-associations.closed")
        }

        @Test
        fun `will callback back to mapping service 3 times before given up`() {
          await untilAsserted {
            MappingExtension.mappingServer.verify(3, getRequestedFor(urlEqualTo("/mapping/non-associations/non-association-id/$NON_ASSOCIATION_ID")))
          }
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, atLeast(3)).trackEvent(
              Mockito.eq("nonAssociation-close-failed"),
              org.mockito.kotlin.check {
                assertThat(it["nonAssociationId"]).isEqualTo(NON_ASSOCIATION_ID.toString())
                assertThat(it["offender1"]).isEqualTo(OFFENDER_NO_1)
                assertThat(it["offender2"]).isEqualTo(OFFENDER_NO_2)
                assertThat(it["sequence"]).isEqualTo("1")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsNonAssociationDlqClient!!.countAllMessagesOnQueue(nonAssociationDlqUrl!!).get()
          } matches { it == 1 }
        }
      }
    }
  }

  private fun publishNonAssociationDomainEvent(eventType: String) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(nonAssociationMessagePayload(NON_ASSOCIATION_ID, eventType))
        .messageAttributes(
          mapOf("eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build()),
        ).build(),
    ).get()
  }

  fun nonAssociationMessagePayload(nonAssociationId: Long, eventType: String) =
    """{"eventType":"$eventType", "additionalInformation": {"id":"$nonAssociationId"}, "version": "1.0", "description": "description", "occurredAt": "2023-09-01T17:09:56.199944267+01:00"}"""
}
