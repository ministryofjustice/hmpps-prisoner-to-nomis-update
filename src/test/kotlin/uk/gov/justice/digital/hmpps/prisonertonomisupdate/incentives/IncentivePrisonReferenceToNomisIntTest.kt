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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.incentivePrisonLevelChangedMessagePayload
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

class IncentivePrisonReferenceToNomisIntTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a incentives prison level changed message for a create`() {
    incentivesApi.stubPrisonIncentiveLevelGet()
    nomisApi.stubNomisPrisonIncentiveLevelNotFound()
    nomisApi.stubNomisPrisonIncentiveLevelCreate()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(incentivePrisonLevelChangedMessagePayload("MDI", "STD"))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("incentives.prison-level.changed").build(),
          ),
        ).build(),
    ).get()

    await untilCallTo { awsSqsIncentiveClient.countMessagesOnQueue(incentiveQueueUrl).get() } matches { it == 0 }
    await untilCallTo { incentivesApi.getCountFor("/incentive/prison-levels/MDI/level/STD") } matches { it == 1 }
    await untilCallTo { nomisApi.postCountFor("/incentives/prison/MDI") } matches { it == 1 }
    nomisApi.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/incentives/prison/MDI"))
        .withRequestBody(WireMock.matchingJsonPath("levelCode", WireMock.equalTo("STD")))
        .withRequestBody(WireMock.matchingJsonPath("defaultOnAdmission", WireMock.equalTo("true"))),
    )
    verify(telemetryClient).trackEvent(
      eq("prison-incentive-level-inserted"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["code"]).isEqualTo("STD")
        Assertions.assertThat(it["defaultOnAdmission"]).isEqualTo("true")
        Assertions.assertThat(it["active"]).isEqualTo("true")
      },
      isNull(),
    )
  }

  @Test
  fun `will consume a incentives prison level changed message for an update`() {
    incentivesApi.stubPrisonIncentiveLevelGet(defaultOnAdmission = false)
    nomisApi.stubNomisPrisonIncentiveLevel()
    nomisApi.stubNomisPrisonIncentiveLevelUpdate()

    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(incentivePrisonLevelChangedMessagePayload("MDI", "STD"))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue("incentives.prison-level.changed").build(),
          ),
        ).build(),
    ).get()

    await untilCallTo { awsSqsIncentiveClient.countMessagesOnQueue(incentiveQueueUrl).get() } matches { it == 0 }
    await untilCallTo { incentivesApi.getCountFor("/incentive/prison-levels/MDI/level/STD") } matches { it == 1 }
    await untilCallTo { nomisApi.putCountFor("/incentives/prison/MDI/code/STD") } matches { it == 1 }
    nomisApi.verify(
      WireMock.putRequestedFor(WireMock.urlEqualTo("/incentives/prison/MDI/code/STD"))
        .withRequestBody(WireMock.matchingJsonPath("levelCode", WireMock.equalTo("STD")))
        .withRequestBody(WireMock.matchingJsonPath("defaultOnAdmission", WireMock.equalTo("false"))),
    )
    verify(telemetryClient).trackEvent(
      eq("prison-incentive-level-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["code"]).isEqualTo("STD")
        Assertions.assertThat(it["active"]).isEqualTo("true")
        Assertions.assertThat(it["defaultOnAdmission"]).isEqualTo("false")
      },
      isNull(),
    )
  }
}
