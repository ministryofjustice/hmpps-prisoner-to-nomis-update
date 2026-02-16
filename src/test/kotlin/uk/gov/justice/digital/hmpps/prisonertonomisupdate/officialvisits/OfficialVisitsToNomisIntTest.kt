package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase

class OfficialVisitsToNomisIntTest : SqsIntegrationTestBase() {
  @Nested
  @DisplayName("official-visits-api.visit.created")
  inner class OfficialVisitCreated {

    @Nested
    @DisplayName("when DPS is the origin of a official visit create")
    inner class WhenDpsCreated {

      @BeforeEach
      fun setUp() {
        publishCreateOfficialVisitDomainEvent(officialVisitId = "12345", prisonId = "MDI", offenderNo = "A1234KT", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("official-visit-create-success"),
          check {
            assertThat(it["dpsOfficialVisitId"]).isEqualTo("12345")
            assertThat(it["offenderNo"]).isEqualTo("A1234KT")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visitor.created")
  inner class VisitorSlotCreated {

    @Nested
    @DisplayName("when DPS is the origin of a visitor create")
    inner class WhenDpsCreated {

      @BeforeEach
      fun setUp() {
        publishCreateOfficialVisitorDomainEvent(officialVisitorId = "7765", officialVisitId = "12345", contactId = "9855", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("official-visitor-create-success"),
          check {
            assertThat(it["dpsOfficialVisitorId"]).isEqualTo("7765")
            assertThat(it["dpsOfficialVisitId"]).isEqualTo("12345")
            assertThat(it["dpsContactId"]).isEqualTo("9855")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Suppress("SameParameterValue")
  private fun publishCreateOfficialVisitDomainEvent(
    officialVisitId: String,
    source: String = "DPS",
    prisonId: String,
    offenderNo: String,
  ) {
    with("official-visits-api.visit.created") {
      publishDomainEvent(eventType = this, payload = visitMessagePayload(eventType = this, officialVisitId = officialVisitId, source = source, prisonId = prisonId, offenderNo = offenderNo))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishCreateOfficialVisitorDomainEvent(
    officialVisitorId: String,
    officialVisitId: String,
    contactId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visitor.created") {
      publishDomainEvent(eventType = this, payload = visitorMessagePayload(eventType = this, officialVisitorId = officialVisitorId, officialVisitId = officialVisitId, source = source, prisonId = prisonId, contactId = contactId))
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

fun visitMessagePayload(
  eventType: String,
  officialVisitId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
  offenderNo: String = "A1234KT",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "officialVisitId": "$officialVisitId",
        "prisonId": "$prisonId",
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
fun visitorMessagePayload(
  eventType: String,
  officialVisitorId: String,
  officialVisitId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
  contactId: String = "1234",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "officialVisitorId": "$officialVisitorId",
        "officialVisitId": "$officialVisitId",
        "prisonId": "$prisonId",
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "CONTACT_ID",
            "value": "$contactId"
          }
        ]
      }
    }
    """
