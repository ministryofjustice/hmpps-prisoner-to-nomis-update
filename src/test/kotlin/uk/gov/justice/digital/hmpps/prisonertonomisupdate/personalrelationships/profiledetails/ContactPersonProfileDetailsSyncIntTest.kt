package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiMockServer

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
        verifyTelemetry("contact-person-domestic-status-created", "A1234BC", 54321, 12345)
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
    profileType: StringValuePattern,
    profileCode: StringValuePattern,
  ) {
    nomisApi.verify(
      putRequestedFor(urlPathEqualTo("/prisoners/$prisonerNumber/profile-details"))
        .withRequestBody(matchingJsonPath("profileType", profileType))
        .withRequestBody(matchingJsonPath("profileCode", profileCode)),
    )
  }

  private fun verifyTelemetry(
    event: String,
    offenderNo: String = "A1234BC",
    domesticStatusId: Long = 54321,
    bookingId: Long = 12345,
  ) {
    verify(telemetryClient).trackEvent(
      eq(event),
      check {
        assertThat(it).containsExactlyInAnyOrderEntriesOf(
          mapOf(
            "offenderNo" to offenderNo,
            "domesticStatusId" to domesticStatusId.toString(),
            "bookingId" to bookingId.toString(),
          ),
        )
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
}

fun domesticStatusCreatedEvent(
  prisonerNumber: String = "A1234BC",
  domesticStatusId: Long = 12345,
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
