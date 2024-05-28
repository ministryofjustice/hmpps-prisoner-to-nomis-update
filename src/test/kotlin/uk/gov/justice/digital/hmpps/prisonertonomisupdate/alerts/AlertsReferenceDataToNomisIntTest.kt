package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase

class AlertsReferenceDataToNomisIntTest : SqsIntegrationTestBase() {
  @Nested
  @DisplayName("prisoner-alerts.alert-code-created")
  inner class AlertCodeCreated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-created", alertCode = "ABC")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("alert-code-create-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-code-updated")
  inner class AlertCodeUpdated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-updated", alertCode = "ABC")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("alert-code-update-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-code-deactivated")
  inner class AlertCodeDeactivated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-deactivated", alertCode = "ABC")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the deactivate`() {
        verify(telemetryClient).trackEvent(
          eq("alert-code-deactivate-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-code-reactivated")
  inner class AlertCodeReactivated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-reactivated", alertCode = "ABC")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the reactivate`() {
        verify(telemetryClient).trackEvent(
          eq("alert-code-reactivate-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-type-created")
  inner class AlertTypeCreated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-created", alertCode = "ABC")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("alert-type-create-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-type-updated")
  inner class AlertTypeUpdated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-updated", alertCode = "ABC")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("alert-type-update-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-type-deactivated")
  inner class AlertTypeDeactivated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-deactivated", alertCode = "ABC")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the deactivate`() {
        verify(telemetryClient).trackEvent(
          eq("alert-type-deactivate-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-type-reactivated")
  inner class AlertTypeReactivated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-reactivated", alertCode = "ABC")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the reactivate`() {
        verify(telemetryClient).trackEvent(
          eq("alert-type-reactivate-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  private fun publishAlertReferenceDataDomainEvent(
    eventType: String,
    alertCode: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          alertReferenceDataMessagePayload(
            eventType = eventType,
            alertCode = alertCode,
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

fun alertReferenceDataMessagePayload(
  eventType: String,
  alertCode: String,
) =
  //language=JSON
  """
    {
      "eventType":"$eventType", 
      "description": "An alert code or type has been changed in the alerts reference data service",
      "occurredAt": "2024-05-28T13:00:33.2534984Z",
      "additionalInformation": {
        "url":"https://alerts-api.hmpps.service.justice.gov.uk/alert-codes/$alertCode", 
        "alertCode": "$alertCode",
        "source": "DPS",
        "reason": "USER"
      }
    }
    """
