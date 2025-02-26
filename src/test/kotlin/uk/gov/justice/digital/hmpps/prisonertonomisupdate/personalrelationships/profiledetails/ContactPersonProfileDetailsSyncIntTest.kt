package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.BAD_GATEWAY
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiMockServer
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class ContactPersonProfileDetailsSyncIntTest(
  @Autowired private val nomisApi: ProfileDetailsNomisApiMockServer,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
) : SqsIntegrationTestBase() {

  @Nested
  inner class ProfileDetailsCreatedEvent {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() = runTest {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
      }

      @Test
      fun `should synchronise domestic status`() {
        nomisApi.stubPutProfileDetails(offenderNo = "A1234BC", created = true, bookingId = 12345)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(
            prisonerNumber = "A1234BC",
            domesticStatusId = 54321,
            source = "DPS",
          ),
        ).also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall(prisonerNumber = "A1234BC")
        verifyNomisPutProfileDetails(
          prisonerNumber = "A1234BC",
          profileType = equalTo("MARITAL"),
          profileCode = equalTo("M"),
        )
        verifyTelemetry(
          telemetryType = "created",
          offenderNo = "A1234BC",
          domesticStatusId = 54321,
          bookingId = 12345,
        )
      }

      @Test
      fun `should raise updated telemetry`() {
        nomisApi.stubPutProfileDetails(created = false)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall()
        verifyNomisPutProfileDetails()
        verifyTelemetry(
          telemetryType = "updated",
          bookingId = 12345,
        )
      }

      @Test
      fun `should ignore if change performed in NOMIS`() {
        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(source = "NOMIS"),
        ).also { waitForAnyProcessingToComplete() }

        dpsApi.verify(count = 0, getRequestedFor(urlPathEqualTo("/sync/A1234BC/domestic-status")))
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
        verifyTelemetry(telemetryType = "ignored", ignoreReason = "Domestic status was created in NOMIS")
      }
    }

    @Nested
    inner class Failures {
      @Test
      fun `should fail if call to DPS fails`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", errorStatus = BAD_GATEWAY)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall()
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
        verifyTelemetry(
          telemetryType = "error",
          errorReason = "502 Bad Gateway from GET http://localhost:8099/sync/A1234BC/domestic-status",
        )
      }

      @Test
      fun `should fail if call to DPS returns not found`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", errorStatus = NOT_FOUND)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall()
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
        verifyTelemetry(
          telemetryType = "error",
          errorReason = "404 Not Found from GET http://localhost:8099/sync/A1234BC/domestic-status",
        )
      }

      @Test
      fun `should fail if call to NOMIS fails`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
        nomisApi.stubPutProfileDetails(errorStatus = BAD_REQUEST)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall()
        verifyNomisPutProfileDetails()
        verifyTelemetry(
          telemetryType = "error",
          errorReason = "400 Bad Request from PUT http://localhost:8082/prisoners/A1234BC/profile-details",
        )
      }
    }
  }

  private fun verifyDpsApiCall(prisonerNumber: String = "A1234BC") {
    dpsApi.verify(
      getRequestedFor(urlPathEqualTo("/sync/$prisonerNumber/domestic-status")),
    )
  }

  private fun verifyNomisPutProfileDetails(
    prisonerNumber: String = "A1234BC",
    profileType: StringValuePattern = equalTo("MARITAL"),
    profileCode: StringValuePattern = equalTo("M"),
  ) {
    nomisApi.verify(
      putRequestedFor(urlPathEqualTo("/prisoners/$prisonerNumber/profile-details"))
        .withRequestBody(matchingJsonPath("profileType", profileType))
        .withRequestBody(matchingJsonPath("profileCode", profileCode)),
    )
  }

  private fun verifyTelemetry(
    telemetryType: String,
    offenderNo: String = "A1234BC",
    domesticStatusId: Long = 54321,
    bookingId: Long? = null,
    ignoreReason: String? = null,
    errorReason: String? = null,
  ) {
    verify(telemetryClient).trackEvent(
      eq("contact-person-domestic-status-$telemetryType"),
      check {
        assertThat(it["offenderNo"]).isEqualTo(offenderNo)
        assertThat(it["domesticStatusId"]).isEqualTo(domesticStatusId.toString())
        bookingId?.run { assertThat(it["bookingId"]).isEqualTo(this.toString()) }
        ignoreReason?.run { assertThat(it["reason"]).isEqualTo(this) }
        errorReason?.run { assertThat(it["error"]).isEqualTo(this) }
      },
      isNull(),
    )
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
            "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun waitForDlqMessage() = await untilCallTo {
    personalRelationshipsDlqClient!!.countAllMessagesOnQueue(personalRelationshipsDlqUrl!!).get()
  } matches { it == 1 }
}

fun domesticStatusCreatedEvent(
  prisonerNumber: String = "A1234BC",
  domesticStatusId: Long = 54321,
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"personal-relationships-api.domestic-status.created", 
      "additionalInformation": {
        "domesticStatusId": $domesticStatusId,
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "prisonerNumber",
            "value": "$prisonerNumber"
          }
        ]
      }
    }
    """
