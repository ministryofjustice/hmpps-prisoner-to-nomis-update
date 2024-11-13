package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonDpsApiExtension.Companion.contact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

class ContactPersonToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: ContactPersonNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: ContactPersonMappingApiMockServer

  private val dpsApi: ContactPersonDpsApiMockServer = dpsContactPersonServer

  @Nested
  @DisplayName("contacts-api.contact.created")
  inner class ContactCreated {

    @Nested
    @DisplayName("when DPS is the origin of a Contact create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactId = 1234567L
        private val nomisPersonId = 1234567L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdOrNull(dpsContactId = dpsContactId, null)
          dpsApi.stubGetContact(
            contactId = dpsContactId,
            contact().copy(
              id = dpsContactId,
              firstName = "John",
              lastName = "Smith",
              middleName = "Steve",
              dateOfBirth = LocalDate.parse("1965-07-19"),
              interpreterRequired = true,
              gender = "M",
              domesticStatus = "S",
              title = "MR",
              isStaff = true,
              languageCode = "EN",
            ),
          )
          nomisApi.stubCreatePerson(createPersonResponse().copy(personId = nomisPersonId))
          mappingApi.stubCreatePersonMapping()
          publishCreateContactDomainEvent(contactId = dpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contactperson-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the contact created`() {
          verify(telemetryClient).trackEvent(
            eq("contactperson-create-success"),
            check {
              assertThat(it).containsEntry("dpsContactId", dpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get contact details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact/$dpsContactId")))
        }

        @Test
        fun `will create the person in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons")))
        }

        @Test
        fun `the created alert will contain details of the DPS alert`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("firstName", "John")
              .withRequestBodyJsonPath("lastName", "Smith")
              .withRequestBodyJsonPath("middleName", "Steve")
              .withRequestBodyJsonPath("dateOfBirth", "1965-07-19")
              .withRequestBodyJsonPath("genderCode", "M")
              .withRequestBodyJsonPath("titleCode", "MR")
              .withRequestBodyJsonPath("languageCode", "EN")
              .withRequestBodyJsonPath("interpreterRequired", true)
              .withRequestBodyJsonPath("domesticStatusCode", "S")
              .withRequestBodyJsonPath("isStaff", true),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/person"))
              .withRequestBodyJsonPath("dpsId", "$dpsContactId")
              .withRequestBodyJsonPath("nomisId", nomisPersonId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }
    }
  }

  private fun publishCreateContactDomainEvent(
    contactId: String,
  ) {
    publishContactDomainEvent("contacts-api.contact.created", contactId = contactId)
  }

  private fun publishContactDomainEvent(
    eventType: String,
    contactId: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          contactMessagePayload(
            eventType = eventType,
            contactId = contactId,
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

fun contactMessagePayload(
  eventType: String,
  contactId: String,
) =
  //language=JSON
  """
    {
      "eventType":"$eventType", 
      "detailUrl":"https://somecallback", 
      "additionalInformation": {
        "contactId": "$contactId"
      }
    }
    """
