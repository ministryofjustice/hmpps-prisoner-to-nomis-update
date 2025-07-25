package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase

class PrisonerRestrictionToNomisIntTest : SqsIntegrationTestBase() {
  @Nested
  @DisplayName("personal-relationships-api.prisoner-restriction.created")
  inner class RestrictionCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Restriction create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreatePrisonerRestrictionDomainEvent(prisonerRestrictionId = "12345", offenderNo = "A1234KT", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("prisoner-restriction-create-ignored"),
          eq(mapOf("dpsRestrictionId" to "12345", "offenderNo" to "A1234KT")),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("personal-relationships-api.prisoner-restriction.updated")
  inner class RestrictionUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Restriction update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdatePrisonerRestrictionDomainEvent(prisonerRestrictionId = "12345", offenderNo = "A1234KT", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("prisoner-restriction-update-ignored"),
          eq(mapOf("dpsRestrictionId" to "12345", "offenderNo" to "A1234KT")),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("personal-relationships-api.prisoner-restriction.deleted")
  inner class RestrictionDelete {

    @Nested
    @DisplayName("when NOMIS is the origin of a Restriction delete")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishDeletePrisonerRestrictionDomainEvent(prisonerRestrictionId = "12345", offenderNo = "A1234KT", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("prisoner-restriction-delete-ignored"),
          eq(mapOf("dpsRestrictionId" to "12345", "offenderNo" to "A1234KT")),
          isNull(),
        )
      }
    }
  }

  @Suppress("SameParameterValue")
  private fun publishCreatePrisonerRestrictionDomainEvent(prisonerRestrictionId: String, offenderNo: String, source: String = "DPS") {
    with("personal-relationships-api.prisoner-restriction.created") {
      publishDomainEvent(eventType = this, payload = prisonerRestrictionMessagePayload(eventType = this, prisonerRestrictionId = prisonerRestrictionId, offenderNo = offenderNo, source = source))
    }
  }

  private fun publishUpdatePrisonerRestrictionDomainEvent(prisonerRestrictionId: String, offenderNo: String, source: String = "DPS") {
    with("personal-relationships-api.prisoner-restriction.updated") {
      publishDomainEvent(eventType = this, payload = prisonerRestrictionMessagePayload(eventType = this, prisonerRestrictionId = prisonerRestrictionId, offenderNo = offenderNo, source = source))
    }
  }

  private fun publishDeletePrisonerRestrictionDomainEvent(prisonerRestrictionId: String, offenderNo: String, source: String = "DPS") {
    with("personal-relationships-api.prisoner-restriction.deleted") {
      publishDomainEvent(eventType = this, payload = prisonerRestrictionMessagePayload(eventType = this, prisonerRestrictionId = prisonerRestrictionId, offenderNo = offenderNo, source = source))
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
}

fun prisonerRestrictionMessagePayload(
  eventType: String,
  prisonerRestrictionId: String,
  source: String = "DPS",
  offenderNo: String = "A1234KT",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "prisonerRestrictionId": "$prisonerRestrictionId",
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$offenderNo"
          }
        ]
      }
    }
    """
