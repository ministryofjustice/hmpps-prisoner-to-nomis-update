package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.StringValuePattern
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AlertMappingDto
import java.time.LocalDate
import java.util.UUID

class AlertsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertNomisApiMockServer: AlertsNomisApiMockServer

  @Autowired
  private lateinit var alertMappingApiMockServer: AlertsMappingApiMockServer

  @Nested
  @DisplayName("prisoner-alerts.alert-created")
  inner class AlertCreated {
    @Nested
    @DisplayName("when NOMIS is the origin of a Alert create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setup() {
        publishCreateAlertDomainEvent(source = AlertSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignore the create`() {
        verify(telemetryClient).trackEvent(
          eq("alert-create-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to create the Alert in NOMIS`() {
        alertNomisApiMockServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Alert create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("Happy path")
      inner class HappyPath {
        private val offenderNo = "A1234KT"
        private val dpsAlertId = UUID.randomUUID().toString()
        private val nomisBookingId = 43217L
        private val nomisAlertSequence = 3L

        @BeforeEach
        fun setUp() {
          alertsDpsApi.stubGetAlert(
            dpsAlert().copy(
              alertUuid = UUID.fromString(dpsAlertId),
              prisonNumber = offenderNo,
              alertCode = dpsAlert().alertCode.copy(code = "HPI"),
              activeFrom = LocalDate.parse("2023-03-03"),
              activeTo = LocalDate.parse("2023-06-06"),
              isActive = false,
              createdBy = "BOBBY.BEANS",
              authorisedBy = "Security Team",
              description = "Added for good reasons",
            ),
          )
          alertNomisApiMockServer.stubPostAlert(offenderNo, alert = createAlertResponse(bookingId = nomisBookingId, alertSequence = nomisAlertSequence))
          alertMappingApiMockServer.stubPostMapping()
          publishCreateAlertDomainEvent(offenderNo = offenderNo, alertUuid = dpsAlertId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("alert-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get alert details`() {
          alertsDpsApi.verify(getRequestedFor(urlMatching("/alerts/$dpsAlertId")))
        }

        @Test
        fun `will create the alert in NOMIS`() {
          alertNomisApiMockServer.verify(postRequestedFor(urlEqualTo("/prisoners/$offenderNo/alerts")))
        }

        @Test
        fun `the created alert will contain details of the DPS alert`() {
          alertNomisApiMockServer.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("alertCode", "HPI")
              .withRequestBodyJsonPath("date", "2023-03-03")
              .withRequestBodyJsonPath("isActive", false)
              .withRequestBodyJsonPath("createUsername", "BOBBY.BEANS")
              .withRequestBodyJsonPath("expiryDate", "2023-06-06")
              .withRequestBodyJsonPath("comment", "Added for good reasons")
              .withRequestBodyJsonPath("authorisedBy", "Security Team"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          alertMappingApiMockServer.verify(postRequestedFor(urlEqualTo("/mapping/alerts")))
        }

        @Test
        fun `the created mapping will contain the IDs`() {
          alertMappingApiMockServer.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("dpsAlertId", dpsAlertId)
              .withRequestBodyJsonPath("nomisBookingId", nomisBookingId)
              .withRequestBodyJsonPath("nomisAlertSequence", nomisAlertSequence)
              .withRequestBodyJsonPath("mappingType", AlertMappingDto.MappingType.DPS_CREATED.name),
          )
        }
      }
    }
  }

  private fun publishCreateAlertDomainEvent(
    offenderNo: String = "A1234KT",
    alertUuid: String = UUID.randomUUID().toString(),
    source: AlertSource = AlertSource.ALERTS_SERVICE,
    alertCode: String = "HPI",
  ) {
    publishAlertDomainEvent("prisoner-alerts.alert-created", offenderNo, alertUuid, source, alertCode)
  }

  private fun publishAlertDomainEvent(
    eventType: String,
    offenderNo: String,
    alertUuid: String,
    source: AlertSource,
    alertCode: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          alertMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
            alertUuid = alertUuid,
            source = source,
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

fun alertMessagePayload(
  eventType: String,
  offenderNo: String,
  alertUuid: String,
  source: AlertSource,
  alertCode: String,
) =
  //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "url":"https://somecallback", 
        "alertUuid": "$alertUuid",
        "prisonNumber": "$offenderNo", 
        "source": "${source.name}",
        "alertCode": "$alertCode"
      }
    }
    """

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, pattern: StringValuePattern): RequestPatternBuilder =
  this.withRequestBody(matchingJsonPath(path, pattern))

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, equalTo: Any): RequestPatternBuilder =
  this.withRequestBodyJsonPath(path, equalTo(equalTo.toString()))
