package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsDpsApiExtension.Companion.alertsDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath

class AlertsReferenceDataToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Nested
  @DisplayName("prisoner-alerts.alert-code-created")
  inner class AlertCodeCreated {
    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      val code = "ABC"

      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertCode(code)
        alertsNomisApiMockServer.stubCreateAlertCode()
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-created", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-codes/$code")))
      }

      @Test
      fun `will POST the data to NOMIS`() {
        alertsNomisApiMockServer.verify(
          postRequestedFor(urlMatching("/alerts/codes"))
            .withRequestBodyJsonPath("code", code)
            .withRequestBodyJsonPath("description", dpsAlertCodeReferenceData(code).description)
            .withRequestBodyJsonPath("listSequence", dpsAlertCodeReferenceData(code).listSequence),
        )
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
      val code = "ABC"

      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertCode(code)
        alertsNomisApiMockServer.stubUpdateAlertCode(code)
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-updated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-codes/$code")))
      }

      @Test
      fun `will PUT the data to NOMIS`() {
        alertsNomisApiMockServer.verify(
          putRequestedFor(urlMatching("/alerts/codes/$code"))
            .withRequestBodyJsonPath("description", dpsAlertCodeReferenceData(code).description),
        )
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
      val code = "ABC"

      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertCode(code, dpsAlertCodeReferenceData(code).copy(isActive = false))
        alertsNomisApiMockServer.stubDeactivateAlertCode(code)
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-deactivated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-codes/$code")))
      }

      @Test
      fun `will PUT the deactivate request NOMIS`() {
        alertsNomisApiMockServer.verify(
          putRequestedFor(urlMatching("/alerts/codes/$code/deactivate")),
        )
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

    @Nested
    @DisplayName("when code is no longer inactive")
    inner class NoLongerInactive {
      val code = "ABC"

      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertCode(code, dpsAlertCodeReferenceData(code).copy(isActive = true))
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-deactivated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-codes/$code")))
      }

      @Test
      fun `will not PUT the deactivate request NOMIS`() {
        alertsNomisApiMockServer.verify(
          0,
          putRequestedFor(urlMatching("/alerts/codes/$code/deactivate")),
        )
      }

      @Test
      fun `will send telemetry event showing the deactivate event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("alert-code-deactivate-ignored"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-code-reactivated")
  inner class AlertCodeReactivated {
    val code = "ABC"

    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertCode(code, dpsAlertCodeReferenceData(code).copy(isActive = true))
        alertsNomisApiMockServer.stubReactivateAlertCode(code)
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-reactivated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-codes/$code")))
      }

      @Test
      fun `will PUT the reactivate request NOMIS`() {
        alertsNomisApiMockServer.verify(
          putRequestedFor(urlMatching("/alerts/codes/$code/reactivate")),
        )
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

    @Nested
    @DisplayName("when code is inactive again")
    inner class WhenInactiveAgain {
      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertCode(code, dpsAlertCodeReferenceData(code).copy(isActive = false))
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-code-reactivated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-codes/$code")))
      }

      @Test
      fun `will not PUT the reactivate request NOMIS`() {
        alertsNomisApiMockServer.verify(
          0,
          putRequestedFor(urlMatching("/alerts/codes/$code/deactivate")),
        )
      }

      @Test
      fun `will send telemetry event showing the reactivate is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("alert-code-reactivate-ignored"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-type-created")
  inner class AlertTypeCreated {
    val code = "XYZ"

    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertType(code)
        alertsNomisApiMockServer.stubCreateAlertType()
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-created", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-types/$code")))
      }

      @Test
      fun `will POST the data to NOMIS`() {
        alertsNomisApiMockServer.verify(
          postRequestedFor(urlMatching("/alerts/types"))
            .withRequestBodyJsonPath("code", code)
            .withRequestBodyJsonPath("description", dpsAlertTypeReferenceData(code).description)
            .withRequestBodyJsonPath("listSequence", dpsAlertTypeReferenceData(code).listSequence),
        )
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
    val code = "XYZ"

    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertType(code)
        alertsNomisApiMockServer.stubUpdateAlertType(code)
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-updated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-types/$code")))
      }

      @Test
      fun `will PUT the data to NOMIS`() {
        alertsNomisApiMockServer.verify(
          putRequestedFor(urlMatching("/alerts/types/$code"))
            .withRequestBodyJsonPath("description", dpsAlertTypeReferenceData(code).description),
        )
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
    val code = "XYZ"

    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertType(code, dpsAlertTypeReferenceData(code).copy(isActive = false))
        alertsNomisApiMockServer.stubDeactivateAlertType(code)
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-deactivated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-types/$code")))
      }

      @Test
      fun `will PUT the deactivate request NOMIS`() {
        alertsNomisApiMockServer.verify(
          putRequestedFor(urlMatching("/alerts/types/$code/deactivate")),
        )
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

    @Nested
    @DisplayName("when type is active again")
    inner class WhenTypeActive {
      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertType(code, dpsAlertTypeReferenceData(code).copy(isActive = true))
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-deactivated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-types/$code")))
      }

      @Test
      fun `will not PUT the deactivate request NOMIS`() {
        alertsNomisApiMockServer.verify(
          0,
          putRequestedFor(urlMatching("/alerts/types/$code/deactivate")),
        )
      }

      @Test
      fun `will send telemetry event showing the deactivate is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("alert-type-deactivate-ignored"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-alerts.alert-type-reactivated")
  inner class AlertTypeReactivated {
    val code = "XYZ"

    @Nested
    @DisplayName("when all goes ok")
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertType(code, dpsAlertTypeReferenceData(code).copy(isActive = true))
        alertsNomisApiMockServer.stubReactivateAlertType(code)
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-reactivated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-types/$code")))
      }

      @Test
      fun `will PUT the reactivate request NOMIS`() {
        alertsNomisApiMockServer.verify(
          putRequestedFor(urlMatching("/alerts/types/$code/reactivate")),
        )
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

    @Nested
    @DisplayName("when type is inactive again")
    inner class WhenTypeInActive {
      @BeforeEach
      fun setUp() {
        alertsDpsApi.stubGetAlertType(code, dpsAlertTypeReferenceData(code).copy(isActive = false))
        publishAlertReferenceDataDomainEvent(eventType = "prisoner-alerts.alert-type-reactivated", alertCode = code)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will retrieve the data from DPS`() {
        alertsDpsApi.verify(getRequestedFor(urlMatching("/alert-types/$code")))
      }

      @Test
      fun `will not PUT the reactivate request NOMIS`() {
        alertsNomisApiMockServer.verify(
          0,
          putRequestedFor(urlMatching("/alerts/types/$code/deactivate")),
        )
      }

      @Test
      fun `will send telemetry event showing the reactivate is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("alert-type-reactivate-ignored"),
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
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "description": "An alert code or type has been changed in the alerts reference data service",
      "detailUrl":"https://alerts-api.hmpps.service.justice.gov.uk/alert-codes/$alertCode", 
      "occurredAt": "2024-05-28T13:00:33.2534984Z",
      "additionalInformation": {
        "alertCode": "$alertCode",
        "source": "DPS"
      }
    }
    """
