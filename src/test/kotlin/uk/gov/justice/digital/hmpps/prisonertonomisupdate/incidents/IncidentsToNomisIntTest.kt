package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase

class IncidentsToNomisIntTest : SqsIntegrationTestBase() {

  private val dpsApi = IncidentsDpsApiExtension.Companion.incidentsDpsApi

  @Nested
  @DisplayName("incident.report.created")
  inner class IncidentCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of the create")
    inner class WhenNomisCreated {
      // @TODO
    }

    @Nested
    @DisplayName("when DPS is the origin of the create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetIncident()
          publishCreateIncidentDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("incidents-create-success"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("incident.report.amended")
  inner class IncidentUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of the update")
    inner class WhenNomisUpdated {
      // @TODO
    }

    @Nested
    @DisplayName("when DPS is the origin of the update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetIncident()
          publishAmendIncidentDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("incidents-update-success"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  private fun publishCreateIncidentDomainEvent(
    incidentId: String = "565643",
    source: IncidentSource = IncidentSource.DPS,
  ) {
    publishIncidentDomainEvent("incident.report.created", incidentId, source)
  }

  private fun publishAmendIncidentDomainEvent(
    incidentId: String = "565643",
    source: IncidentSource = IncidentSource.DPS,
  ) {
    publishIncidentDomainEvent("incident.report.amended", incidentId, source)
  }

  private fun publishIncidentDomainEvent(
    eventType: String,
    incidentId: String,
    source: IncidentSource,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          incidentMessagePayload(
            eventType = eventType,
            incidentId = incidentId,
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

fun incidentMessagePayload(
  eventType: String,
  incidentId: String,
  source: IncidentSource,
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "description": "0196e947-67c6-7efb-94d4-f2bfbf9ae6c9 An incident report has been amended",
      "occurredAt": "2025-06-02T11:25:30.636855943+01:00",
      "additionalInformation": {
        "source": "${source.name}",
        "id": "$incidentId",
        "reportReference": "1234",
        "whatChanged": "QUESTIONS"
      }
   }
   """
