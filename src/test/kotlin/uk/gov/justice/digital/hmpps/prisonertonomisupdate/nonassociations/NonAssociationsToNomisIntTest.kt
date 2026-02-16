package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApiServer
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val NON_ASSOCIATION_ID = 12345L
private const val OFFENDER_NO_1 = "A1234AA"
private const val OFFENDER_NO_2 = "B1234BB"
private const val OFFENDER_TO_REMOVE = "A4567ZQ"
private const val OFFENDER_TO_SURVIVE = "A7869AW"

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
        nomisApi.stubNonAssociationCreate("""{ "typeSequence": 1 }""")
        mappingServer.stubGetMappingGivenNonAssociationIdWithError(NON_ASSOCIATION_ID, 404)
        mappingServer.stubCreateNonAssociation()
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
          eq("non-association-create-success"),
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

        nomisApi.verify(postRequestedFor(urlEqualTo("/non-associations")))
      }

      @Test
      fun `will create a mapping`() {
        waitForCreateProcessingToBeComplete()

        await untilAsserted {
          mappingServer.verify(
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
        mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
        publishNonAssociationDomainEvent("non-associations.created")
      }

      @Test
      fun `will not create an non-association in NOMIS`() {
        waitForCreateProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("non-association-create-duplicate"),
          org.mockito.kotlin.check {
            assertThat(it["nonAssociationId"]).isEqualTo("$NON_ASSOCIATION_ID")
          },
          isNull(),
        )

        nomisApi.verify(
          0,
          postRequestedFor(urlEqualTo("/non-associations")),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenNonAssociationIdWithError(NON_ASSOCIATION_ID, 404)
        mappingServer.stubCreateNonAssociationWithErrorFollowedBySlowSuccess()
        nomisApi.stubNonAssociationCreate("""{ "typeSequence": 1 }""")
        nonAssociationsApiServer.stubGetNonAssociation(NON_ASSOCIATION_ID, nonAssociationApiResponse)
        publishNonAssociationDomainEvent("non-associations.created")

        await untilCallTo { nonAssociationsApiServer.getCountFor("/legacy/api/non-associations/$NON_ASSOCIATION_ID") } matches { it == 1 }
        await untilCallTo { nomisApi.postCountFor("/non-associations") } matches { it == 1 }
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
        nomisApi.verify(
          1,
          postRequestedFor(urlEqualTo("/non-associations")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS non-association is created`() {
        await untilAsserted {
          mappingServer.verify(
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
        nonAssociationsApiServer.stubGetNonAssociation(id = NON_ASSOCIATION_ID, response = nonAssociationApiResponse)
        mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
        nomisApi.stubNonAssociationAmend(OFFENDER_NO_1, OFFENDER_NO_2)
        publishNonAssociationDomainEvent("non-associations.amended")
      }

      @Test
      fun `will callback back to nonAssociation service to get more details`() {
        await untilAsserted {
          nonAssociationsApiServer.verify(getRequestedFor(urlEqualTo("/legacy/api/non-associations/$NON_ASSOCIATION_ID")))
        }
      }

      @Test
      fun `will update a nonAssociation in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(
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
            Mockito.eq("non-association-amend-success"),
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
          nonAssociationsApiServer.stubGetNonAssociationWithErrorFollowedBySlowSuccess(
            id = NON_ASSOCIATION_ID,
            response = nonAssociationApiResponse,
          )
          mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
          nomisApi.stubNonAssociationAmend(OFFENDER_NO_1, OFFENDER_NO_2)
          publishNonAssociationDomainEvent("non-associations.amended")
        }

        @Test
        fun `will callback back to nonAssociation service twice to get more details`() {
          await untilAsserted {
            nonAssociationsApiServer.verify(2, getRequestedFor(urlEqualTo("/legacy/api/non-associations/$NON_ASSOCIATION_ID")))
            verify(telemetryClient).trackEvent(Mockito.eq("non-association-amend-success"), any(), isNull())
          }
        }

        @Test
        fun `will eventually update the nonAssociation in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/non-associations/offender/$OFFENDER_NO_1/ns-offender/$OFFENDER_NO_2/sequence/1")))
            verify(telemetryClient).trackEvent(Mockito.eq("non-association-amend-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(Mockito.eq("non-association-amend-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenServiceKeepsFailing {

        @BeforeEach
        fun setUp() {
          nonAssociationsApiServer.stubGetNonAssociation(id = NON_ASSOCIATION_ID, response = nonAssociationApiResponse)
          mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
          nomisApi.stubNonAssociationAmendWithError(OFFENDER_NO_1, OFFENDER_NO_2, 503)
          publishNonAssociationDomainEvent("non-associations.amended")
        }

        @Test
        fun `will callback back to nonAssociation service 3 times before given up`() {
          await untilAsserted {
            nonAssociationsApiServer.verify(3, getRequestedFor(urlEqualTo("/legacy/api/non-associations/$NON_ASSOCIATION_ID")))
          }
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              Mockito.eq("non-association-amend-failed"),
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
        mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
        nomisApi.stubNonAssociationClose(OFFENDER_NO_1, OFFENDER_NO_2)
        publishNonAssociationDomainEvent("non-associations.closed")
      }

      @Test
      fun `will close a non-association in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(putRequestedFor(urlEqualTo("/non-associations/offender/$OFFENDER_NO_1/ns-offender/$OFFENDER_NO_2/sequence/1/close")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("non-association-close-success"),
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
          mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
          nomisApi.stubNonAssociationCloseWithErrorFollowedBySlowSuccess(OFFENDER_NO_1, OFFENDER_NO_2)
          publishNonAssociationDomainEvent("non-associations.closed")
        }

        @Test
        fun `will callback back to mapping service twice to get more details`() {
          await untilAsserted {
            mappingServer.verify(2, getRequestedFor(urlEqualTo("/mapping/non-associations/non-association-id/$NON_ASSOCIATION_ID")))
          }
        }

        @Test
        fun `will eventually close the non-association in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(2, putRequestedFor(urlEqualTo("/non-associations/offender/$OFFENDER_NO_1/ns-offender/$OFFENDER_NO_2/sequence/1/close")))
            verify(telemetryClient).trackEvent(Mockito.eq("non-association-close-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(Mockito.eq("non-association-close-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          nonAssociationsApiServer.stubGetNonAssociation(id = NON_ASSOCIATION_ID, response = nonAssociationApiResponse)
          mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
          nomisApi.stubNonAssociationCloseWithError(OFFENDER_NO_1, OFFENDER_NO_2, 503)
          publishNonAssociationDomainEvent("non-associations.closed")
        }

        @Test
        fun `will callback back to mapping service 3 times before given up`() {
          await untilAsserted {
            mappingServer.verify(3, getRequestedFor(urlEqualTo("/mapping/non-associations/non-association-id/$NON_ASSOCIATION_ID")))
          }
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, atLeast(3)).trackEvent(
              Mockito.eq("non-association-close-failed"),
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
  inner class DeleteNonAssociation {
    @Nested
    inner class WhenNonAssociationHasJustBeenDeletedByNonAssociationService {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenNonAssociationId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
        nomisApi.stubNonAssociationDelete(OFFENDER_NO_1, OFFENDER_NO_2)
        mappingServer.stubDeleteNonAssociationMapping(NON_ASSOCIATION_ID)
        publishNonAssociationDomainEvent("non-associations.deleted")
      }

      @Test
      fun `will delete the non-association in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/non-associations/offender/$OFFENDER_NO_1/ns-offender/$OFFENDER_NO_2/sequence/1")))
        }
      }

      @Test
      fun `will delete the mapping`() {
        await untilAsserted {
          mappingServer.verify(deleteRequestedFor(urlEqualTo("/mapping/non-associations/non-association-id/$NON_ASSOCIATION_ID")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("non-association-delete-success"),
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
  }

  @Nested
  inner class Merge {
    private fun mappingNA(nonAssociationId: Long, first: String, second: String, sequence: Int) = """{
            "nonAssociationId": $nonAssociationId,
            "firstOffenderNo": "$first",
            "secondOffenderNo": "$second",
            "nomisTypeSequence": $sequence,
            "mappingType": "NON_ASSOCIATION_CREATED"
          }"""
    private fun dpsNA(nonAssociationId: Long, offender: String, other: String, effectiveDateTime: String) = """ {
          "id": $nonAssociationId,
          "offenderNo": "$offender",
          "effectiveDate": "$effectiveDateTime",
          "reasonCode": "VIC",
          "reasonDescription": "Victim",
          "typeCode": "WING",
          "typeDescription": "Do Not Locate on Same Wing",
          "comments": "comments",
          "offenderNonAssociation": {
            "offenderNo": "$other",
            "reasonCode": "PER",
            "reasonDescription": "Perpetrator"
          }
        }"""
    private fun nomisNA(first: String, second: String, sequence: Int, effectiveDate: String) = """{
          "offenderNo": "$first",
          "nsOffenderNo": "$second",
          "typeSequence": $sequence,
          "effectiveDate": "$effectiveDate",
          "reason": "VIC",
          "recipReason": "PER",
          "type": "LAND",
          "updatedBy": "del_gen"
        }"""

    @BeforeEach
    fun setUp() {
      mappingServer.stubGetThirdParties(
        OFFENDER_TO_REMOVE,
        OFFENDER_TO_SURVIVE,
        """
        [
          ${mappingNA(1001, OFFENDER_TO_REMOVE, "COMMON", 1)},
          ${mappingNA(1002, "COMMON", OFFENDER_TO_SURVIVE, 1)},
          ${mappingNA(1011, OFFENDER_TO_REMOVE, "COMMON2", 1)},
          ${mappingNA(1012, "COMMON2", OFFENDER_TO_SURVIVE, 1)}
        ]""",
      )

      nonAssociationsApiServer.stubGetNonAssociation(
        1001,
        dpsNA(1001, OFFENDER_TO_REMOVE, "COMMON", "2026-01-01T10:10:10"),
      )
      nonAssociationsApiServer.stubGetNonAssociation(
        1002,
        dpsNA(1002, "COMMON", OFFENDER_TO_SURVIVE, "2026-05-05T10:10:10"),
      )
      nonAssociationsApiServer.stubGetNonAssociation(
        1011,
        dpsNA(1011, OFFENDER_TO_REMOVE, "COMMON2", "2026-07-07T10:10:10"),
      )
      nonAssociationsApiServer.stubGetNonAssociation(
        1012,
        dpsNA(1012, "COMMON2", OFFENDER_TO_SURVIVE, "2026-09-09T10:10:10"),
      )

      // In Nomis, the merge has already completed so OFFENDER_TO_REMOVE doesn't exist
      nomisApi.stubGetNonAssociationsAll(
        OFFENDER_TO_SURVIVE,
        "COMMON",
        "[${nomisNA(OFFENDER_TO_SURVIVE, "COMMON", 1, "2026-01-01")},${nomisNA(OFFENDER_TO_SURVIVE, "COMMON", 2, "2026-05-05")}]",
      )
      nomisApi.stubGetNonAssociationsAll(
        "COMMON",
        OFFENDER_TO_SURVIVE,
        "[${nomisNA("COMMON", OFFENDER_TO_SURVIVE, 1, "2026-01-01")},${nomisNA("COMMON", OFFENDER_TO_SURVIVE, 2, "2026-05-05")}]",
      )
      nomisApi.stubGetNonAssociationsAll(
        OFFENDER_TO_SURVIVE,
        "COMMON2",
        "[${nomisNA(OFFENDER_TO_SURVIVE, "COMMON2", 1, "2026-09-09")},${nomisNA(OFFENDER_TO_SURVIVE, "COMMON2", 2, "2023-11-11")},${nomisNA(OFFENDER_TO_SURVIVE, "COMMON2", 3, "2026-07-07")}]",
      )
      nomisApi.stubGetNonAssociationsAll(
        "COMMON2",
        OFFENDER_TO_SURVIVE,
        "[${nomisNA("COMMON2", OFFENDER_TO_SURVIVE, 1, "2026-09-09")},${nomisNA("COMMON2", OFFENDER_TO_SURVIVE, 2, "2023-11-11")},${nomisNA("COMMON2", OFFENDER_TO_SURVIVE, 3, "2026-07-07")}]",
      )
      mappingServer.stubSetSequence(1002, 2)
      mappingServer.stubSetSequence(1011, 3)
      mappingServer.stubPutMergeNonAssociation(OFFENDER_TO_REMOVE, OFFENDER_TO_SURVIVE)
      sendMergeEvent(OFFENDER_TO_REMOVE, OFFENDER_TO_SURVIVE)
    }

    @Test
    fun `will correct the mapping`() {
      await untilAsserted {
        mappingServer.verify(
          putRequestedFor(urlEqualTo("/mapping/non-associations/merge/from/$OFFENDER_TO_REMOVE/to/$OFFENDER_TO_SURVIVE")),
        )
      }
      mappingServer.verify(
        putRequestedFor(urlEqualTo("/mapping/non-associations/non-association-id/1002/sequence/2")),
      )
      mappingServer.verify(
        putRequestedFor(urlEqualTo("/mapping/non-associations/non-association-id/1011/sequence/3")),
      )
    }

    @Test
    fun `will create success telemetry`() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          Mockito.eq("non-association-merge-success"),
          org.mockito.kotlin.check {
            assertThat(it["removedNomsNumber"]).isEqualTo(OFFENDER_TO_REMOVE)
            assertThat(it["nomsNumber"]).isEqualTo(OFFENDER_TO_SURVIVE)
            assertThat(it["reason"]).isEqualTo("MERGE")
            assertThat(it["COMMON"]).isEqualTo("Reset nonAssociationId 1002 sequence from 1 to 2")
            assertThat(it["COMMON2"]).isEqualTo("Reset nonAssociationId 1011 sequence from 1 to 3")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class BookingMoved {
    private val bookingId = 1234567L
    private val movedFromNomsNumber = "A1234AA"
    private val movedToNomsNumber = "B1234BB"
    private val bookingStartDateTime = "2021-07-05T10:55:04"

    @BeforeEach
    fun setUp() {
      nomisApi.stubGetNonAssociationsByBooking(
        bookingId,
        """
          [
            { "offenderNo1": "$movedToNomsNumber", "offenderNo2": "SOME-OTHER" }
          ]
          """,
      )
      mappingServer.stubUpdateList(movedFromNomsNumber, movedToNomsNumber)
    }

    @Test
    fun `will correct the mapping`() {
      sendBookingMovedEvent(bookingId, movedFromNomsNumber, movedToNomsNumber, bookingStartDateTime)
      await untilAsserted {
        mappingServer.verify(
          putRequestedFor(urlEqualTo("/mapping/non-associations/update-list/from/$movedFromNomsNumber/to/$movedToNomsNumber"))
            .withRequestBody(matchingJsonPath("$[0]", equalTo("SOME-OTHER"))),
        )
      }
    }

    @Test
    fun `will create success telemetry`() {
      sendBookingMovedEvent(bookingId, movedFromNomsNumber, movedToNomsNumber, bookingStartDateTime)
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          Mockito.eq("non-association-booking-moved-success"),
          org.mockito.kotlin.check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["movedFromNomsNumber"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["movedToNomsNumber"]).isEqualTo(movedToNomsNumber)
            assertThat(it["bookingStartDateTime"]).isEqualTo(bookingStartDateTime)
            assertThat(it["count"]).isEqualTo("1")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `No affected NAs found`() {
      nomisApi.stubGetNonAssociationsByBooking(bookingId, "[]")
      sendBookingMovedEvent(bookingId, movedFromNomsNumber, movedToNomsNumber, bookingStartDateTime)

      await untilAsserted {
        nomisApi.verify(
          getRequestedFor(urlEqualTo("/non-associations/booking/$bookingId")),
        )
      }
      mappingServer.verify(
        0,
        putRequestedFor(
          urlEqualTo("/mapping/non-associations/update-list/from/$movedFromNomsNumber/to/$movedToNomsNumber"),
        ),
      )
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

  fun nonAssociationMessagePayload(nonAssociationId: Long, eventType: String) = """
    {"eventType":"$eventType", "additionalInformation": {"id":"$nonAssociationId"}, "version": "1.0", "description": "description", "occurredAt": "2023-09-01T17:09:56.199944267+01:00"}"""

  private fun sendMergeEvent(old: String, new: String) {
    awsSqsNonAssociationClient.sendMessage(
      SendMessageRequest.builder().queueUrl(nonAssociationQueueUrl).messageBody(
        mergeDomainEvent(removedOffenderNo = old, offenderNo = new),
      ).build(),
    ).get()
  }

  private fun sendBookingMovedEvent(bookingId: Long, old: String, new: String, bookingStartDateTime: String) {
    awsSqsNonAssociationClient.sendMessage(
      SendMessageRequest.builder().queueUrl(nonAssociationQueueUrl).messageBody(
        bookingMovedDomainEvent(bookingId = bookingId, movedFromNomsNumber = old, movedToNomsNumber = new, bookingStartDateTime = bookingStartDateTime),
      ).build(),
    ).get()
  }
}
