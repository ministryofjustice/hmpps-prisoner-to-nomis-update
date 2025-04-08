package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase

class VisitBalanceToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("prison-visit-allocation.updated")
  inner class VisitBalanceAdjustmentCreated {
    @Nested
    @DisplayName("when NOMIS is the origin of a Visit Balance adjustment create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setup() {
        publishVisitBalanceAdjustmentDomainEvent(source = VisitBalanceAdjustmentSource.NOMIS)

        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create received`() {
        verify(telemetryClient).trackEvent(
          eq("Received prison-visit-allocation.updated event"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a visit balance adjustment create")
    inner class VisitBalanceAdjustmentCreated {
      @Nested
      @DisplayName("when DPS is the origin of a Visit Balance adjustment create")
      inner class WhenDpsCreated {
        @BeforeEach
        fun setup() {
          publishVisitBalanceAdjustmentDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create received`() {
          verify(telemetryClient).trackEvent(
            eq("Received prison-visit-allocation.updated event"),
            check {
              assertThat(it).containsEntry("prisonNumber", "A1234KT")
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun publishVisitBalanceAdjustmentDomainEvent(
    eventType: String = "prison-visit-allocation.updated",
    offenderNo: String = "A1234KT",
    source: VisitBalanceAdjustmentSource = VisitBalanceAdjustmentSource.DPS,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          visitBalanceAdjustmentMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
            source = source,
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}

fun visitBalanceAdjustmentMessagePayload(
  eventType: String,
  offenderNo: String,
  source: VisitBalanceAdjustmentSource,
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "detailUrl":"https://somecallback", 
      "additionalInformation": {
        "source": "${source.name}"
      },
      "personReference": {
        "identifiers": [
          {
            "type" : "NOMS", "value": "$offenderNo"
          }
        ]
      }
    }
    """
