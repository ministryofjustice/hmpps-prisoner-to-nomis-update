package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonDpsApiExtension.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto.MappingType.DPS_CREATED
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
    @DisplayName("when NOMIS is the origin of a Contact create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateContactDomainEvent(contactId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdOrNull(dpsContactId = nomisPersonIdAndDpsContactId, null)
          dpsApi.stubGetContact(
            contactId = nomisPersonIdAndDpsContactId,
            contact().copy(
              id = nomisPersonIdAndDpsContactId,
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
          nomisApi.stubCreatePerson(createPersonResponse().copy(personId = nomisPersonIdAndDpsContactId))
          mappingApi.stubCreatePersonMapping()
          publishCreateContactDomainEvent(contactId = nomisPersonIdAndDpsContactId.toString())
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
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get contact details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact/$nomisPersonIdAndDpsContactId")))
        }

        @Test
        fun `will create the person in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons")))
        }

        @Test
        fun `the created person will contain details of the DPS contact`() {
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
        fun `will create a mapping between the NOMIS and DPS ids even though they are the same`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/person"))
              .withRequestBodyJsonPath("dpsId", "$nomisPersonIdAndDpsContactId")
              .withRequestBodyJsonPath("nomisId", nomisPersonIdAndDpsContactId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdOrNull(dpsContactId = this.nomisPersonIdAndDpsContactId, null)
          dpsApi.stubGetContact(
            contactId = this.nomisPersonIdAndDpsContactId,
            contact().copy(
              id = this.nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePerson(createPersonResponse().copy(personId = this.nomisPersonIdAndDpsContactId))
          mappingApi.stubCreatePersonMappingFollowedBySuccess()
          publishCreateContactDomainEvent(contactId = this.nomisPersonIdAndDpsContactId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contactperson-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-person-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-person-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val nomisPersonIdAndDpsContactId = 1234567L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdOrNull(dpsContactId = this.nomisPersonIdAndDpsContactId, null)
          dpsApi.stubGetContact(
            contactId = this.nomisPersonIdAndDpsContactId,
            contact().copy(
              id = this.nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePerson(createPersonResponse().copy(personId = this.nomisPersonIdAndDpsContactId))
          mappingApi.stubCreatePersonMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonMappingDto(
                  dpsId = this.nomisPersonIdAndDpsContactId.toString(),
                  nomisId = this.nomisPersonIdAndDpsContactId,
                  mappingType = DPS_CREATED,
                ),
                existing = PersonMappingDto(
                  dpsId = this.nomisPersonIdAndDpsContactId.toString(),
                  nomisId = this.nomisPersonIdAndDpsContactId,
                  mappingType = DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateContactDomainEvent(contactId = this.nomisPersonIdAndDpsContactId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contactperson-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contactperson-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.prisoner-contact.created")
  inner class PrisonerContactCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Prisoner Contact create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreatePrisonerContactDomainEvent(prisonerContactId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Prisoner Contact create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsPrisonerContactId = 1234567L
        private val nomisContactId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = dpsPrisonerContactId, null)
          dpsApi.stubGetPrisonerContact(
            prisonerContactId = dpsPrisonerContactId,
            prisonerContact().copy(
              id = dpsPrisonerContactId,
              contactId = nomisPersonIdAndDpsContactId,
              prisonerNumber = "A1234KT",
              contactType = "S",
              relationshipType = "BRO",
              nextOfKin = true,
              emergencyContact = true,
              comments = "Big brother",
              active = true,
              approvedVisitor = true,
              expiryDate = LocalDate.parse("2020-01-01"),
            ),
          )
          nomisApi.stubCreatePersonContact(personId = nomisPersonIdAndDpsContactId, createPersonContactResponse().copy(personContactId = nomisContactId))
          mappingApi.stubCreateContactMapping()
          publishCreatePrisonerContactDomainEvent(prisonerContactId = dpsPrisonerContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the contact created`() {
          verify(telemetryClient).trackEvent(
            eq("contact-create-success"),
            check {
              assertThat(it).containsEntry("dpsPrisonerContactId", dpsPrisonerContactId.toString())
              assertThat(it).containsEntry("nomisContactId", nomisContactId.toString())
              assertThat(it).containsEntry("offenderNo", "A1234KT")
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get prisoner contact details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/prisoner-contact/$dpsPrisonerContactId")))
        }

        @Test
        fun `will create the contact in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact")))
        }

        @Test
        fun `the created contact will contain details of the DPS prisoner contact`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("offenderNo", "A1234KT")
              .withRequestBodyJsonPath("contactTypeCode", "S")
              .withRequestBodyJsonPath("relationshipTypeCode", "BRO")
              .withRequestBodyJsonPath("active", true)
              .withRequestBodyJsonPath("expiryDate", "2020-01-01")
              .withRequestBodyJsonPath("approvedVisitor", true)
              .withRequestBodyJsonPath("nextOfKin", true)
              .withRequestBodyJsonPath("emergencyContact", true)
              .withRequestBodyJsonPath("comment", "Big brother"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/contact"))
              .withRequestBodyJsonPath("dpsId", "$dpsPrisonerContactId")
              .withRequestBodyJsonPath("nomisId", nomisContactId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsPrisonerContactId = 1234567L
        private val nomisContactId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = dpsPrisonerContactId, null)
          dpsApi.stubGetPrisonerContact(
            prisonerContactId = dpsPrisonerContactId,
            prisonerContact().copy(
              id = dpsPrisonerContactId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonContact(personId = nomisPersonIdAndDpsContactId, createPersonContactResponse().copy(personContactId = nomisContactId))
          mappingApi.stubCreateContactMappingFollowedBySuccess()
          publishCreatePrisonerContactDomainEvent(prisonerContactId = dpsPrisonerContactId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsPrisonerContactId = 1234567L
        private val nomisContactId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = dpsPrisonerContactId, null)
          dpsApi.stubGetPrisonerContact(
            prisonerContactId = dpsPrisonerContactId,
            prisonerContact().copy(
              id = dpsPrisonerContactId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonContact(nomisPersonIdAndDpsContactId, createPersonContactResponse().copy(personContactId = nomisContactId))
          mappingApi.stubCreateContactMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonContactMappingDto(
                  dpsId = dpsPrisonerContactId.toString(),
                  nomisId = 999999,
                  mappingType = PersonContactMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonContactMappingDto(
                  dpsId = dpsPrisonerContactId.toString(),
                  nomisId = nomisContactId,
                  mappingType = PersonContactMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreatePrisonerContactDomainEvent(prisonerContactId = dpsPrisonerContactId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the contact in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact")))
          }
        }
      }
    }
  }

  private fun publishCreateContactDomainEvent(contactId: String, source: String = "DPS") {
    with("contacts-api.contact.created") {
      publishDomainEvent(eventType = this, payload = contactMessagePayload(eventType = this, contactId = contactId, source = source))
    }
  }

  private fun publishCreatePrisonerContactDomainEvent(prisonerContactId: String, source: String = "DPS") {
    with("contacts-api.prisoner-contact.created") {
      publishDomainEvent(eventType = this, payload = prisonerContactMessagePayload(eventType = this, prisonerContactId = prisonerContactId, source = source))
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

fun contactMessagePayload(
  eventType: String,
  contactId: String,
  source: String,
) =
  //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "contactId": "$contactId",
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "DPS_CONTACT_ID",
            "value": "$contactId"
          }
        ]
      }
    }
    """

fun prisonerContactMessagePayload(
  eventType: String,
  prisonerContactId: String,
  source: String = "DPS",
  contactId: String = "87654",
  offenderNo: String = "",
) =
  //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "prisonerContactId": "$prisonerContactId",
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$offenderNo"
          },
          {
            "type": "DPS_CONTACT_ID",
            "value": "$contactId"
          }
        ]
      }
    }
    """
