package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.incentiveLevelChangedMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

class IncentiveReferenceToNomisIntTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a incentives level changed message for a create`() {
    incentivesApi.stubIncentiveLevelGet()
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
    await untilCallTo { nomisApi.postCountFor("/reference-domains/domains/IEP_LEVEL/codes") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/reference-domains/domains/IEP_LEVEL/codes"))
        .withRequestBody(WireMock.matchingJsonPath("code", WireMock.equalTo("STD")))
        .withRequestBody(WireMock.matchingJsonPath("description", WireMock.equalTo("Description for STD"))),
    )
  }

  @Test
  fun `will consume a incentives level changed message for an update`() {
    incentivesApi.stubIncentiveLevelGet()
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
    await untilCallTo { nomisApi.putCountFor("/reference-domains/domains/IEP_LEVEL/codes/STD") } matches { it == 1 }
    nomisApi.verify(
      WireMock.putRequestedFor(WireMock.urlEqualTo("/reference-domains/domains/IEP_LEVEL/codes/STD"))
        .withRequestBody(WireMock.matchingJsonPath("code", WireMock.equalTo("STD")))
        .withRequestBody(WireMock.matchingJsonPath("description", WireMock.equalTo("Description for STD"))),
    )
  }
}
