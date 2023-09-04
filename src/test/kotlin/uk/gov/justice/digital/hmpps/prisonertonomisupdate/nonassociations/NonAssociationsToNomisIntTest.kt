package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApiServer

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
        MappingExtension.mappingServer.stubGetMappingGivenNonAssociationInstanceIdWithError(NON_ASSOCIATION_ID, 404)
        MappingExtension.mappingServer.stubCreateNonAssociation()
        publishCreateNonAssociationDomainEvent()
      }

      @Test
      fun `will callback back to non-association service to get more details`() {
        waitForCreateProcessingToBeComplete()

        nonAssociationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/api/non-associations/$NON_ASSOCIATION_ID")))
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

        NomisApiExtension.nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/non-associations")))
      }

      @Test
      fun `will create a mapping`() {
        waitForCreateProcessingToBeComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/non-associations"))
              .withRequestBody(WireMock.matchingJsonPath("nonAssociationId", WireMock.equalTo(NON_ASSOCIATION_ID.toString())))
              .withRequestBody(WireMock.matchingJsonPath("firstOffenderNo", WireMock.equalTo(OFFENDER_NO_1)))
              .withRequestBody(WireMock.matchingJsonPath("secondOffenderNo", WireMock.equalTo(OFFENDER_NO_2)))
              .withRequestBody(WireMock.matchingJsonPath("nomisTypeSequence", WireMock.equalTo("1"))),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForNonAssociation {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetMappingGivenNonAssociationInstanceId(NON_ASSOCIATION_ID, nonAssociationMappingResponse)
        publishCreateNonAssociationDomainEvent()
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
          WireMock.postRequestedFor(WireMock.urlEqualTo("/non-associations")),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetMappingGivenNonAssociationInstanceIdWithError(NON_ASSOCIATION_ID, 404)
        MappingExtension.mappingServer.stubCreateNonAssociationWithErrorFollowedBySlowSuccess()
        NomisApiExtension.nomisApi.stubNonAssociationCreate("""{ "typeSequence": 1 }""")
        nonAssociationsApiServer.stubGetNonAssociation(NON_ASSOCIATION_ID, nonAssociationApiResponse)
        publishCreateNonAssociationDomainEvent()

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
          WireMock.postRequestedFor(WireMock.urlEqualTo("/non-associations")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS non-association is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/non-associations"))
              .withRequestBody(WireMock.matchingJsonPath("nonAssociationId", WireMock.equalTo("$NON_ASSOCIATION_ID")))
              .withRequestBody(WireMock.matchingJsonPath("firstOffenderNo", WireMock.equalTo(OFFENDER_NO_1)))
              .withRequestBody(WireMock.matchingJsonPath("secondOffenderNo", WireMock.equalTo(OFFENDER_NO_2)))
              .withRequestBody(WireMock.matchingJsonPath("nomisTypeSequence", WireMock.equalTo("1"))),
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

  private fun publishCreateNonAssociationDomainEvent() {
    val eventType = "non-associations.created"
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
