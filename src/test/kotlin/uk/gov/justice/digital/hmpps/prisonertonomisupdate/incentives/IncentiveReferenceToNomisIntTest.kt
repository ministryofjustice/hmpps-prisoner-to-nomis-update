package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.incentiveLevelChangedMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.incentiveLevelsReorderedMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

class IncentiveReferenceToNomisIntTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a incentives level changed message for a create`() {
    incentivesApi.stubGlobalIncentiveLevelGet()
    nomisApi.stubNomisGlobalIncentiveLevelNotFound()
    nomisApi.stubNomisGlobalIncentiveLevelCreate()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(incentiveLevelChangedMessagePayload("STD"))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("incentives.level.changed").build(),
          ),
        ).build(),
    ).get()

    await untilCallTo { awsSqsIncentiveClient.countMessagesOnQueue(incentiveQueueUrl).get() } matches { it == 0 }
    await untilCallTo { incentivesApi.getCountFor("/incentive/levels/STD?with-inactive=true") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/incentives/reference-codes") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/incentives/reference-codes"))
        .withRequestBody(WireMock.matchingJsonPath("code", WireMock.equalTo("STD")))
        .withRequestBody(WireMock.matchingJsonPath("description", WireMock.equalTo("Description for STD"))),
    )
    verify(telemetryClient).trackEvent(
      eq("global-incentive-level-inserted"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["code"]).isEqualTo("STD")
        Assertions.assertThat(it["description"]).isEqualTo("Description for STD")
        Assertions.assertThat(it["active"]).isEqualTo("true")
      },
      isNull(),
    )
  }

  @Test
  fun `will consume a incentives level changed message for an update`() {
    incentivesApi.stubGlobalIncentiveLevelGet()
    nomisApi.stubNomisGlobalIncentiveLevel()
    nomisApi.stubNomisGlobalIncentiveLevelUpdate()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(incentiveLevelChangedMessagePayload("STD"))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("incentives.level.changed").build(),
          ),
        ).build(),
    ).get()

    await untilCallTo { awsSqsIncentiveClient.countMessagesOnQueue(incentiveQueueUrl).get() } matches { it == 0 }
    await untilCallTo { incentivesApi.getCountFor("/incentive/levels/STD?with-inactive=true") } matches { it == 1 }
    await untilCallTo { nomisApi.putCountFor("/incentives/reference-codes/STD") } matches { it == 1 }
    nomisApi.verify(
      WireMock.putRequestedFor(WireMock.urlEqualTo("/incentives/reference-codes/STD"))
        .withRequestBody(WireMock.matchingJsonPath("code", WireMock.equalTo("STD")))
        .withRequestBody(WireMock.matchingJsonPath("description", WireMock.equalTo("Description for STD"))),
    )
    verify(telemetryClient).trackEvent(
      eq("global-incentive-level-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["code"]).isEqualTo("STD")
        Assertions.assertThat(it["description"]).isEqualTo("Description for STD")
        Assertions.assertThat(it["active"]).isEqualTo("true")
      },
      isNull(),
    )
  }

  @Test
  fun `will consume a incentives levels reorder message`() {
    incentivesApi.stubGlobalIncentiveLevelsGet()
    nomisApi.stubNomisGlobalIncentiveLevelReorder()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(incentiveLevelsReorderedMessagePayload())
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("incentives.levels.reordered").build(),
          ),
        ).build(),
    ).get()

    await untilCallTo { awsSqsIncentiveClient.countMessagesOnQueue(incentiveQueueUrl).get() } matches { it == 0 }
    await untilCallTo { incentivesApi.getCountFor("/incentive/levels?with-inactive=true") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/incentives/reference-codes/reorder") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/incentives/reference-codes/reorder"))
        .withRequestBody(WireMock.equalToJson("[\"BAS\",\"STD\",\"ENH\",\"EN2\",\"EN3\",\"ENT\"]")),
    )
    verify(telemetryClient).trackEvent(
      eq("global-incentive-levels-reordered"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["orderedIepCodes"]).isEqualTo("[BAS, STD, ENH, EN2, EN3, ENT]")
      },
      isNull(),
    )
  }
}
