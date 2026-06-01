package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.system.CapturedOutput
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import java.util.UUID

class CourtSchedulerScheduleIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("Court appearance created")
  inner class CourtAppearanceCreated {
    // TODO replace with proper tests
    @Test
    fun `will consume message`(output: CapturedOutput) {
      publishCourtAppearanceDomainEvent(UUID.randomUUID(), "A1234BC")
      await untilAsserted {
        assertThat(output.out).contains("Ignoring DPS court appearance")
      }
    }
  }

  private fun publishCourtAppearanceDomainEvent(dpsId: UUID, prisonerNumber: String, source: String = "DPS", eventType: String = "person.court-appearance.scheduled") {
    with(eventType) {
      publishDomainEvent(eventType = this, payload = messagePayload(eventType = this, id = dpsId, prisonerNumber = prisonerNumber, source = source))
    }
  }

  private fun publishDomainEvent(
    eventType: String,
    payload: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(payload)
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
  fun messagePayload(
    eventType: String,
    prisonerNumber: String,
    id: UUID,
    source: String,
  ) = //language=JSON
    """
    {
      "description":"Some event", 
      "eventType":"$eventType", 
      "additionalInformation": {
        "id": "$id",
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$prisonerNumber"
          }
        ]
      }
    }
    """
}
