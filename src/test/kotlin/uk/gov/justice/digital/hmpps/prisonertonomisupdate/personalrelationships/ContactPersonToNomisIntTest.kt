package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmploymentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createContactPersonRestrictionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonEmploymentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonResponse
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
  @DisplayName("contacts-api.contact.deleted")
  inner class ContactDeleted {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact delete")
    inner class WhenNomisDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteContactDomainEvent(contactId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-delete-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdOrNull(dpsContactId = nomisPersonIdAndDpsContactId, PersonMappingDto(dpsId = nomisPersonIdAndDpsContactId.toString(), nomisId = nomisPersonIdAndDpsContactId, mappingType = DPS_CREATED))
          nomisApi.stubDeletePerson(personId = nomisPersonIdAndDpsContactId)
          mappingApi.stubDeleteByDpsContactId(nomisPersonIdAndDpsContactId)
          publishDeleteContactDomainEvent(contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the contact deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contactperson-delete-success"),
            check {
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will delete the person in NOMIS`() {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId")))
        }

        @Test
        fun `will delete a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            deleteRequestedFor(urlEqualTo("/mapping/contact-person/person/dps-contact-id/$nomisPersonIdAndDpsContactId")),
          )
        }
      }

      @Nested
      inner class WhenMappingDoesNotExist {
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdOrNull(dpsContactId = nomisPersonIdAndDpsContactId, null)
          publishDeleteContactDomainEvent(contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the contact deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contactperson-delete-skipped"),
            check {
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will not delete the person in NOMIS`() {
          nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId")))
        }

        @Test
        fun `will not delete a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            0,
            deleteRequestedFor(urlEqualTo("/mapping/contact-person/person/dps-contact-id/$nomisPersonIdAndDpsContactId")),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact.updated")
  inner class ContactUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateContactDomainEvent(contactId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactId(dpsContactId = nomisPersonIdAndDpsContactId, PersonMappingDto(dpsId = nomisPersonIdAndDpsContactId.toString(), nomisId = nomisPersonIdAndDpsContactId, mappingType = DPS_CREATED))
          dpsApi.stubGetContact(
            nomisPersonIdAndDpsContactId,
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
              deceasedDate = LocalDate.parse("2024-08-19"),
            ),
          )
          nomisApi.stubUpdatePerson(personId = nomisPersonIdAndDpsContactId)
          publishUpdateContactDomainEvent(contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the contact updated`() {
          verify(telemetryClient).trackEvent(
            eq("contactperson-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will update the person in NOMIS`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId"))
              .withRequestBodyJsonPath("firstName", "John")
              .withRequestBodyJsonPath("lastName", "Smith")
              .withRequestBodyJsonPath("middleName", "Steve")
              .withRequestBodyJsonPath("dateOfBirth", "1965-07-19")
              .withRequestBodyJsonPath("genderCode", "M")
              .withRequestBodyJsonPath("titleCode", "MR")
              .withRequestBodyJsonPath("languageCode", "EN")
              .withRequestBodyJsonPath("interpreterRequired", true)
              .withRequestBodyJsonPath("domesticStatusCode", "S")
              .withRequestBodyJsonPath("deceasedDate", "2024-08-19")
              .withRequestBodyJsonPath("isStaff", true),
          )
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

  @Nested
  @DisplayName("contacts-api.prisoner-contact.updated")
  inner class PrisonerContactUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdatePrisonerContactDomainEvent(prisonerContactId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Prisoner Contact update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val nomisPersonIdAndDpsContactId = 54321L
        private val nomisContactId = 974592L
        private val dpsPrisonerContactId = 2751731L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerContactId(dpsPrisonerContactId = dpsPrisonerContactId, PersonContactMappingDto(dpsId = dpsPrisonerContactId.toString(), nomisId = nomisContactId, mappingType = PersonContactMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetPrisonerContact(
            dpsPrisonerContactId,
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
          nomisApi.stubUpdatePersonContact(personId = nomisPersonIdAndDpsContactId, contactId = nomisContactId)
          publishUpdatePrisonerContactDomainEvent(prisonerContactId = dpsPrisonerContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the contact updated`() {
          verify(telemetryClient).trackEvent(
            eq("contact-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("dpsPrisonerContactId", dpsPrisonerContactId.toString())
              assertThat(it).containsEntry("nomisContactId", nomisContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will update the person contact in NOMIS`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact/$nomisContactId"))
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
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-address.created")
  inner class ContactAddressCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Address create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateContactAddressDomainEvent(contactAddressId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-address-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Address create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactAddressId = 1234567L
        private val nomisAddressId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressIdOrNull(dpsContactAddressId = dpsContactAddressId, null)
          dpsApi.stubGetContactAddress(
            contactAddressId = dpsContactAddressId,
            contactAddress().copy(
              contactAddressId = dpsContactAddressId,
              contactId = nomisPersonIdAndDpsContactId,
              addressType = "HOME",
              flat = "1A",
              property = "Brown Court",
              street = "Brown Street",
              area = "Brownshire",
              cityCode = "25343",
              countyCode = "S.YORKSHIRE",
              countryCode = "ENG",
              postcode = "LD5 7BW",
              mailFlag = true,
              noFixedAddress = false,
              primaryAddress = true,
              comments = "Big house",
              startDate = LocalDate.parse("2020-01-01"),
              endDate = LocalDate.parse("2026-01-01"),
            ),
          )
          nomisApi.stubCreatePersonAddress(personId = nomisPersonIdAndDpsContactId, createPersonAddressResponse().copy(personAddressId = nomisAddressId))
          mappingApi.stubCreateAddressMapping()
          publishCreateContactAddressDomainEvent(contactAddressId = dpsContactAddressId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the contact created`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-create-success"),
            check {
              assertThat(it).containsEntry("dpsContactAddressId", dpsContactAddressId.toString())
              assertThat(it).containsEntry("nomisAddressId", nomisAddressId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get address details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-address/$dpsContactAddressId")))
        }

        @Test
        fun `will create the address in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address")))
        }

        @Test
        fun `the created address will contain details of the DPS contact address`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("typeCode", "HOME")
              .withRequestBodyJsonPath("flat", "1A")
              .withRequestBodyJsonPath("premise", "Brown Court")
              .withRequestBodyJsonPath("street", "Brown Street")
              .withRequestBodyJsonPath("locality", "Brownshire")
              .withRequestBodyJsonPath("postcode", "LD5 7BW")
              .withRequestBodyJsonPath("cityCode", "25343")
              .withRequestBodyJsonPath("countyCode", "S.YORKSHIRE")
              .withRequestBodyJsonPath("countryCode", "ENG")
              .withRequestBodyJsonPath("noFixedAddress", false)
              .withRequestBodyJsonPath("primaryAddress", true)
              .withRequestBodyJsonPath("mailAddress", true)
              .withRequestBodyJsonPath("comment", "Big house")
              .withRequestBodyJsonPath("startDate", "2020-01-01")
              .withRequestBodyJsonPath("endDate", "2026-01-01"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/address"))
              .withRequestBodyJsonPath("dpsId", "$dpsContactAddressId")
              .withRequestBodyJsonPath("nomisId", nomisAddressId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsContactAddressId = 1234567L
        private val nomisAddressId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressIdOrNull(dpsContactAddressId = dpsContactAddressId, null)
          dpsApi.stubGetContactAddress(
            contactAddressId = dpsContactAddressId,
            contactAddress().copy(
              contactAddressId = dpsContactAddressId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonAddress(personId = nomisPersonIdAndDpsContactId, createPersonAddressResponse().copy(personAddressId = nomisAddressId))
          mappingApi.stubCreateAddressMappingFollowedBySuccess()
          publishCreateContactAddressDomainEvent(contactAddressId = dpsContactAddressId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-address-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-address-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the address in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-address-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsContactAddressId = 1234567L
        private val nomisAddressId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressIdOrNull(dpsContactAddressId = dpsContactAddressId, null)
          dpsApi.stubGetContactAddress(
            contactAddressId = dpsContactAddressId,
            contactAddress().copy(
              contactAddressId = dpsContactAddressId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonAddress(nomisPersonIdAndDpsContactId, createPersonAddressResponse().copy(personAddressId = nomisAddressId))
          mappingApi.stubCreateAddressMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonAddressMappingDto(
                  dpsId = dpsContactAddressId.toString(),
                  nomisId = 999999,
                  mappingType = PersonAddressMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonAddressMappingDto(
                  dpsId = dpsContactAddressId.toString(),
                  nomisId = nomisAddressId,
                  mappingType = PersonAddressMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateContactAddressDomainEvent(contactAddressId = dpsContactAddressId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-address-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the address in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-address-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-address.updated")
  inner class ContactAddressUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a address update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateContactAddressDomainEvent(contactAddressId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-address-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a address update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val nomisPersonIdAndDpsContactId = 54321L
        private val nomisAddressId = 974592L
        private val dpsContactAddressId = 2751731L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressId(dpsContactAddressId = dpsContactAddressId, PersonAddressMappingDto(dpsId = dpsContactAddressId.toString(), nomisId = nomisAddressId, mappingType = PersonAddressMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetContactAddress(
            contactAddressId = dpsContactAddressId,
            contactAddress().copy(
              contactAddressId = dpsContactAddressId,
              contactId = nomisPersonIdAndDpsContactId,
              addressType = "HOME",
              flat = "1A",
              property = "Brown Court",
              street = "Brown Street",
              area = "Brownshire",
              cityCode = "25343",
              countyCode = "S.YORKSHIRE",
              countryCode = "ENG",
              postcode = "LD5 7BW",
              mailFlag = true,
              noFixedAddress = false,
              primaryAddress = true,
              verified = true,
              comments = "Big house",
              startDate = LocalDate.parse("2020-01-01"),
              endDate = LocalDate.parse("2026-01-01"),
            ),
          )
          nomisApi.stubUpdatePersonAddress(personId = nomisPersonIdAndDpsContactId, addressId = nomisAddressId)
          publishUpdateContactAddressDomainEvent(contactAddressId = dpsContactAddressId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the contact updated`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("dpsContactAddressId", dpsContactAddressId.toString())
              assertThat(it).containsEntry("nomisAddressId", nomisAddressId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will update the person address in NOMIS`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address/$nomisAddressId"))
              .withRequestBodyJsonPath("typeCode", "HOME")
              .withRequestBodyJsonPath("flat", "1A")
              .withRequestBodyJsonPath("premise", "Brown Court")
              .withRequestBodyJsonPath("street", "Brown Street")
              .withRequestBodyJsonPath("locality", "Brownshire")
              .withRequestBodyJsonPath("postcode", "LD5 7BW")
              .withRequestBodyJsonPath("cityCode", "25343")
              .withRequestBodyJsonPath("countyCode", "S.YORKSHIRE")
              .withRequestBodyJsonPath("countryCode", "ENG")
              .withRequestBodyJsonPath("noFixedAddress", false)
              .withRequestBodyJsonPath("primaryAddress", true)
              .withRequestBodyJsonPath("mailAddress", true)
              .withRequestBodyJsonPath("validatedPAF", true)
              .withRequestBodyJsonPath("comment", "Big house")
              .withRequestBodyJsonPath("startDate", "2020-01-01")
              .withRequestBodyJsonPath("endDate", "2026-01-01"),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-address.deleted")
  inner class ContactAddressDeleted {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Address delete")
    inner class WhenNomisDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteContactAddressDomainEvent(contactAddressId = "12345", source = "NOMIS", contactId = "38383")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-address-delete-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Address delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactAddressId = 1234567L
        private val nomisAddressId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressIdOrNull(
            dpsContactAddressId = dpsContactAddressId,
            PersonAddressMappingDto(
              dpsId = dpsContactAddressId.toString(),
              nomisId = nomisAddressId,
              mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
            ),
          )
          nomisApi.stubDeletePersonAddress(personId = nomisPersonIdAndDpsContactId, addressId = nomisAddressId)
          mappingApi.stubDeleteByNomisAddressId(nomisAddressId = nomisAddressId)
          publishDeleteContactAddressDomainEvent(contactAddressId = dpsContactAddressId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-delete-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the address deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-delete-success"),
            check {
              assertThat(it).containsEntry("dpsContactAddressId", dpsContactAddressId.toString())
              assertThat(it).containsEntry("nomisAddressId", nomisAddressId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will delete the address in NOMIS`() {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address/$nomisAddressId")))
        }

        @Test
        fun `will delete the address mapping`() {
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")))
        }
      }

      @Nested
      @DisplayName("Address mapping already deleted")
      inner class AddressMappingMissing {
        private val dpsContactAddressId = 1234567L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressIdOrNull(dpsContactAddressId = dpsContactAddressId, null)
          publishDeleteContactAddressDomainEvent(contactAddressId = dpsContactAddressId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the address deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-delete-skipped"),
            check {
              assertThat(it).containsEntry("dpsContactAddressId", dpsContactAddressId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-email.created")
  inner class ContactEmailCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Email create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateContactEmailDomainEvent(contactEmailId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-email-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Email create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactEmailId = 1234567L
        private val nomisEmailId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmailIdOrNull(dpsContactEmailId = dpsContactEmailId, null)
          dpsApi.stubGetContactEmail(
            contactEmailId = dpsContactEmailId,
            contactEmail().copy(
              contactEmailId = dpsContactEmailId,
              contactId = nomisPersonIdAndDpsContactId,
              emailAddress = "test@test.com",
            ),
          )
          nomisApi.stubCreatePersonEmail(personId = nomisPersonIdAndDpsContactId, createPersonEmailResponse().copy(emailAddressId = nomisEmailId))
          mappingApi.stubCreateEmailMapping()
          publishCreateContactEmailDomainEvent(contactEmailId = dpsContactEmailId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-email-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the email created`() {
          verify(telemetryClient).trackEvent(
            eq("contact-email-create-success"),
            check {
              assertThat(it).containsEntry("dpsContactEmailId", dpsContactEmailId.toString())
              assertThat(it).containsEntry("nomisInternetAddressId", nomisEmailId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get email details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-email/$dpsContactEmailId")))
        }

        @Test
        fun `will create the email in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/email")))
        }

        @Test
        fun `the created email will contain details of the DPS contact email`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("email", "test@test.com"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/email"))
              .withRequestBodyJsonPath("dpsId", "$dpsContactEmailId")
              .withRequestBodyJsonPath("nomisId", nomisEmailId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsContactEmailId = 1234567L
        private val nomisEmailId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmailIdOrNull(dpsContactEmailId = dpsContactEmailId, null)
          dpsApi.stubGetContactEmail(
            contactEmailId = dpsContactEmailId,
            contactEmail().copy(
              contactEmailId = dpsContactEmailId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonEmail(personId = nomisPersonIdAndDpsContactId, createPersonEmailResponse().copy(emailAddressId = nomisEmailId))
          mappingApi.stubCreateEmailMappingFollowedBySuccess()
          publishCreateContactEmailDomainEvent(contactEmailId = dpsContactEmailId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-email-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-email-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the email in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-email-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/email")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsContactEmailId = 1234567L
        private val nomisEmailId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmailIdOrNull(dpsContactEmailId = dpsContactEmailId, null)
          dpsApi.stubGetContactEmail(
            contactEmailId = dpsContactEmailId,
            contactEmail().copy(
              contactEmailId = dpsContactEmailId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonEmail(nomisPersonIdAndDpsContactId, createPersonEmailResponse().copy(emailAddressId = nomisEmailId))
          mappingApi.stubCreateEmailMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonEmailMappingDto(
                  dpsId = dpsContactEmailId.toString(),
                  nomisId = 999999,
                  mappingType = PersonEmailMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonEmailMappingDto(
                  dpsId = dpsContactEmailId.toString(),
                  nomisId = nomisEmailId,
                  mappingType = PersonEmailMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateContactEmailDomainEvent(contactEmailId = dpsContactEmailId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-email-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the email in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-email-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/email")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-email.updated")
  inner class ContactEmailUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Email update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateContactEmailDomainEvent(contactEmailId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-email-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Email update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactEmailId = 1234567L
        private val nomisEmailId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmailId(
            dpsContactEmailId = dpsContactEmailId,
            PersonEmailMappingDto(
              dpsId = dpsContactEmailId.toString(),
              nomisId = nomisEmailId,
              mappingType = PersonEmailMappingDto.MappingType.DPS_CREATED,
            ),
          )
          dpsApi.stubGetContactEmail(
            contactEmailId = dpsContactEmailId,
            contactEmail().copy(
              contactEmailId = dpsContactEmailId,
              contactId = nomisPersonIdAndDpsContactId,
              emailAddress = "test@test.com",
            ),
          )
          nomisApi.stubUpdatePersonEmail(personId = nomisPersonIdAndDpsContactId, emailId = nomisEmailId)
          publishUpdateContactEmailDomainEvent(contactEmailId = dpsContactEmailId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("contact-email-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the email updated`() {
          verify(telemetryClient).trackEvent(
            eq("contact-email-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactEmailId", dpsContactEmailId.toString())
              assertThat(it).containsEntry("nomisInternetAddressId", nomisEmailId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get email details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-email/$dpsContactEmailId")))
        }

        @Test
        fun `will update the email in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/email/$nomisEmailId")))
        }

        @Test
        fun `the updated email will contain details of the DPS contact email`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("email", "test@test.com"),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-email.deleted")
  inner class ContactEmailDeleted {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Email delete")
    inner class WhenNomisDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteContactEmailDomainEvent(contactEmailId = "12345", source = "NOMIS", contactId = "38383")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-email-delete-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Email delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactEmailId = 1234567L
        private val nomisEmailId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmailIdOrNull(
            dpsContactEmailId = dpsContactEmailId,
            PersonEmailMappingDto(
              dpsId = dpsContactEmailId.toString(),
              nomisId = nomisEmailId,
              mappingType = PersonEmailMappingDto.MappingType.MIGRATED,
            ),
          )
          nomisApi.stubDeletePersonEmail(personId = nomisPersonIdAndDpsContactId, emailId = nomisEmailId)
          mappingApi.stubDeleteByNomisEmailId(nomisInternetAddressId = nomisEmailId)
          publishDeleteContactEmailDomainEvent(contactEmailId = dpsContactEmailId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("contact-email-delete-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the email deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-email-delete-success"),
            check {
              assertThat(it).containsEntry("dpsContactEmailId", dpsContactEmailId.toString())
              assertThat(it).containsEntry("nomisEmailId", nomisEmailId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will delete the email in NOMIS`() {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/email/$nomisEmailId")))
        }

        @Test
        fun `will delete the email mapping`() {
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/email/nomis-internet-address-id/$nomisEmailId")))
        }
      }

      @Nested
      @DisplayName("Email mapping already deleted")
      inner class EmailMappingMissing {
        private val dpsContactEmailId = 1234567L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmailIdOrNull(dpsContactEmailId = dpsContactEmailId, null)
          publishDeleteContactEmailDomainEvent(contactEmailId = dpsContactEmailId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the email deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-email-delete-skipped"),
            check {
              assertThat(it).containsEntry("dpsContactEmailId", dpsContactEmailId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-phone.created")
  inner class ContactPhoneCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Phone create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateContactPhoneDomainEvent(contactPhoneId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-phone-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Phone create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactPhoneId = 1234567L
        private val nomisPhoneId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactPhoneIdOrNull(dpsContactPhoneId = dpsContactPhoneId, null)
          dpsApi.stubGetContactPhone(
            contactPhoneId = dpsContactPhoneId,
            contactPhone().copy(
              contactPhoneId = dpsContactPhoneId,
              contactId = nomisPersonIdAndDpsContactId,
              phoneNumber = "07973 555 5555",
              phoneType = "MOB",
              extNumber = "x555",
            ),
          )
          nomisApi.stubCreatePersonPhone(personId = nomisPersonIdAndDpsContactId, createPersonPhoneResponse().copy(phoneId = nomisPhoneId))
          mappingApi.stubCreatePhoneMapping()
          publishCreateContactPhoneDomainEvent(contactPhoneId = dpsContactPhoneId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-phone-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the phone created`() {
          verify(telemetryClient).trackEvent(
            eq("contact-phone-create-success"),
            check {
              assertThat(it).containsEntry("dpsContactPhoneId", dpsContactPhoneId.toString())
              assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get phone details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-phone/$dpsContactPhoneId")))
        }

        @Test
        fun `will create the phone in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/phone")))
        }

        @Test
        fun `the created phone will contain details of the DPS contact phone`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("number", "07973 555 5555")
              .withRequestBodyJsonPath("typeCode", "MOB")
              .withRequestBodyJsonPath("extension", "x555"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/phone"))
              .withRequestBodyJsonPath("dpsId", "$dpsContactPhoneId")
              .withRequestBodyJsonPath("nomisId", nomisPhoneId)
              .withRequestBodyJsonPath("dpsPhoneType", "PERSON")
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsContactPhoneId = 1234567L
        private val nomisPhoneId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactPhoneIdOrNull(dpsContactPhoneId = dpsContactPhoneId, null)
          dpsApi.stubGetContactPhone(
            contactPhoneId = dpsContactPhoneId,
            contactPhone().copy(
              contactPhoneId = dpsContactPhoneId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonPhone(personId = nomisPersonIdAndDpsContactId, createPersonPhoneResponse().copy(phoneId = nomisPhoneId))
          mappingApi.stubCreatePhoneMappingFollowedBySuccess()
          publishCreateContactPhoneDomainEvent(contactPhoneId = dpsContactPhoneId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-phone-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-phone-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the phone in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-phone-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/phone")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsContactPhoneId = 1234567L
        private val nomisPhoneId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactPhoneIdOrNull(dpsContactPhoneId = dpsContactPhoneId, null)
          dpsApi.stubGetContactPhone(
            contactPhoneId = dpsContactPhoneId,
            contactPhone().copy(
              contactPhoneId = dpsContactPhoneId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonPhone(nomisPersonIdAndDpsContactId, createPersonPhoneResponse().copy(phoneId = nomisPhoneId))
          mappingApi.stubCreatePhoneMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonPhoneMappingDto(
                  dpsId = dpsContactPhoneId.toString(),
                  nomisId = 999999,
                  dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
                  mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonPhoneMappingDto(
                  dpsId = dpsContactPhoneId.toString(),
                  nomisId = nomisPhoneId,
                  dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
                  mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateContactPhoneDomainEvent(contactPhoneId = dpsContactPhoneId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-phone-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the phone in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-phone-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/phone")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-phone.updated")
  inner class ContactPhoneUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Phone update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateContactPhoneDomainEvent(contactPhoneId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-phone-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Phone update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactPhoneId = 1234567L
        private val nomisPhoneId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactPhoneId(
            dpsContactPhoneId = dpsContactPhoneId,
            PersonPhoneMappingDto(
              dpsId = dpsContactPhoneId.toString(),
              nomisId = nomisPhoneId,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
              mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
            ),
          )
          dpsApi.stubGetContactPhone(
            contactPhoneId = dpsContactPhoneId,
            contactPhone().copy(
              contactPhoneId = dpsContactPhoneId,
              contactId = nomisPersonIdAndDpsContactId,
              phoneNumber = "07973 555 5555",
              phoneType = "MOB",
              extNumber = "x555",
            ),
          )
          nomisApi.stubUpdatePersonPhone(personId = nomisPersonIdAndDpsContactId, phoneId = nomisPhoneId)
          publishUpdateContactPhoneDomainEvent(contactPhoneId = dpsContactPhoneId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("contact-phone-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the phone updated`() {
          verify(telemetryClient).trackEvent(
            eq("contact-phone-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactPhoneId", dpsContactPhoneId.toString())
              assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get phone details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-phone/$dpsContactPhoneId")))
        }

        @Test
        fun `will update the phone in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/phone/$nomisPhoneId")))
        }

        @Test
        fun `the updated phone will contain details of the DPS contact phone`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("number", "07973 555 5555")
              .withRequestBodyJsonPath("typeCode", "MOB")
              .withRequestBodyJsonPath("extension", "x555"),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-phone.deleted")
  inner class ContactPhoneDeleted {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Phone delete")
    inner class WhenNomisDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteContactPhoneDomainEvent(contactPhoneId = "12345", source = "NOMIS", contactId = "38383")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-phone-delete-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Phone delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactPhoneId = 1234567L
        private val nomisPhoneId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactPhoneIdOrNull(
            dpsContactPhoneId = dpsContactPhoneId,
            PersonPhoneMappingDto(
              dpsId = dpsContactPhoneId.toString(),
              nomisId = nomisPhoneId,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
              mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
            ),
          )
          nomisApi.stubDeletePersonPhone(personId = nomisPersonIdAndDpsContactId, phoneId = nomisPhoneId)
          mappingApi.stubDeleteByNomisPhoneId(nomisPhoneId = nomisPhoneId)
          publishDeleteContactPhoneDomainEvent(contactPhoneId = dpsContactPhoneId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("contact-phone-delete-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the phone deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-phone-delete-success"),
            check {
              assertThat(it).containsEntry("dpsContactPhoneId", dpsContactPhoneId.toString())
              assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will delete the phone in NOMIS`() {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/phone/$nomisPhoneId")))
        }

        @Test
        fun `will delete the phone mapping`() {
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")))
        }
      }

      @Nested
      @DisplayName("Phone mapping already deleted")
      inner class PhoneMappingMissing {
        private val dpsContactPhoneId = 1234567L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactPhoneIdOrNull(dpsContactPhoneId = dpsContactPhoneId, null)
          publishDeleteContactPhoneDomainEvent(contactPhoneId = dpsContactPhoneId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the phone deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-phone-delete-skipped"),
            check {
              assertThat(it).containsEntry("dpsContactPhoneId", dpsContactPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-address-phone.created")
  inner class ContactAddressPhoneCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Phone create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateContactAddressPhoneDomainEvent(contactAddressPhoneId = "12345", contactAddressId = "65432", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-address-phone-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Phone create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactAddressPhoneId = 1234567L
        private val dpsContactAddressId = 9836373L
        private val nomisPhoneId = 7654321L
        private val nomisAddressId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId = dpsContactAddressPhoneId, null)
          mappingApi.stubGetByDpsContactAddressId(dpsContactAddressId = dpsContactAddressId, PersonAddressMappingDto(dpsId = dpsContactAddressId.toString(), nomisId = nomisAddressId, PersonAddressMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetContactAddressPhone(
            contactAddressPhoneId = dpsContactAddressPhoneId,
            contactAddressPhone().copy(
              contactPhoneId = dpsContactAddressPhoneId,
              contactId = nomisPersonIdAndDpsContactId,
              contactAddressId = dpsContactAddressId,
              phoneNumber = "07973 555 5555",
              phoneType = "MOB",
              extNumber = "x555",
            ),
          )
          nomisApi.stubCreatePersonAddressPhone(personId = nomisPersonIdAndDpsContactId, addressId = nomisAddressId, createPersonPhoneResponse().copy(phoneId = nomisPhoneId))
          mappingApi.stubCreatePhoneMapping()
          publishCreateContactAddressPhoneDomainEvent(contactAddressPhoneId = dpsContactAddressPhoneId.toString(), contactAddressId = dpsContactAddressId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-phone-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the phone created`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-phone-create-success"),
            check {
              assertThat(it).containsEntry("dpsContactAddressPhoneId", dpsContactAddressPhoneId.toString())
              assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get phone details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-address-phone/$dpsContactAddressPhoneId")))
        }

        @Test
        fun `will create the phone in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address/$nomisAddressId/phone")))
        }

        @Test
        fun `the created phone will contain details of the DPS contact phone`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("number", "07973 555 5555")
              .withRequestBodyJsonPath("typeCode", "MOB")
              .withRequestBodyJsonPath("extension", "x555"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/phone"))
              .withRequestBodyJsonPath("dpsId", "$dpsContactAddressPhoneId")
              .withRequestBodyJsonPath("nomisId", nomisPhoneId)
              .withRequestBodyJsonPath("dpsPhoneType", "ADDRESS")
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsContactAddressPhoneId = 1234567L
        private val dpsContactAddressId = 9836373L
        private val nomisPhoneId = 7654321L
        private val nomisAddressId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressId(dpsContactAddressId = dpsContactAddressId, PersonAddressMappingDto(dpsId = dpsContactAddressId.toString(), nomisId = nomisAddressId, PersonAddressMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetContactAddressPhone(
            contactAddressPhoneId = dpsContactAddressPhoneId,
            contactAddressPhone().copy(
              contactPhoneId = dpsContactAddressPhoneId,
              contactId = nomisPersonIdAndDpsContactId,
              contactAddressId = dpsContactAddressId,
              phoneNumber = "07973 555 5555",
              phoneType = "MOB",
              extNumber = "x555",
            ),
          )
          nomisApi.stubCreatePersonAddressPhone(personId = nomisPersonIdAndDpsContactId, addressId = nomisAddressId, createPersonPhoneResponse().copy(phoneId = nomisPhoneId))
          mappingApi.stubCreatePhoneMappingFollowedBySuccess()
          publishCreateContactAddressPhoneDomainEvent(contactAddressPhoneId = dpsContactAddressPhoneId.toString(), contactAddressId = dpsContactAddressId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-address-phone-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-address-phone-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the phone in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-address-phone-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address/$nomisAddressId/phone")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsContactAddressPhoneId = 1234567L
        private val dpsContactAddressId = 9836373L
        private val nomisPhoneId = 7654321L
        private val nomisAddressId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressId(dpsContactAddressId = dpsContactAddressId, PersonAddressMappingDto(dpsId = dpsContactAddressId.toString(), nomisId = nomisAddressId, PersonAddressMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetContactAddressPhone(
            contactAddressPhoneId = dpsContactAddressPhoneId,
            contactAddressPhone().copy(
              contactPhoneId = dpsContactAddressPhoneId,
              contactId = nomisPersonIdAndDpsContactId,
              contactAddressId = dpsContactAddressId,
              phoneNumber = "07973 555 5555",
              phoneType = "MOB",
              extNumber = "x555",
            ),
          )
          nomisApi.stubCreatePersonAddressPhone(personId = nomisPersonIdAndDpsContactId, addressId = nomisAddressId, createPersonPhoneResponse().copy(phoneId = nomisPhoneId))
          mappingApi.stubCreatePhoneMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonPhoneMappingDto(
                  dpsId = dpsContactAddressPhoneId.toString(),
                  nomisId = 999999,
                  dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
                  mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonPhoneMappingDto(
                  dpsId = dpsContactAddressPhoneId.toString(),
                  nomisId = nomisPhoneId,
                  dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
                  mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateContactAddressPhoneDomainEvent(contactAddressPhoneId = dpsContactAddressPhoneId.toString(), contactAddressId = dpsContactAddressId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-address-phone-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the phone in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-address-phone-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address/$nomisAddressId/phone")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-address-phone.updated")
  inner class ContactAddressPhoneUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Phone update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateContactAddressPhoneDomainEvent(contactAddressPhoneId = "12345", contactAddressId = "65432", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-address-phone-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Phone update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactAddressPhoneId = 1234567L
        private val dpsContactAddressId = 9836373L
        private val nomisPhoneId = 7654321L
        private val nomisAddressId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressPhoneId(
            dpsContactAddressPhoneId = dpsContactAddressPhoneId,
            PersonPhoneMappingDto(
              dpsId = dpsContactAddressPhoneId.toString(),
              nomisId = nomisPhoneId,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
              mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
            ),
          )
          mappingApi.stubGetByDpsContactAddressId(dpsContactAddressId = dpsContactAddressId, PersonAddressMappingDto(dpsId = dpsContactAddressId.toString(), nomisId = nomisAddressId, PersonAddressMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetContactAddressPhone(
            contactAddressPhoneId = dpsContactAddressPhoneId,
            contactAddressPhone().copy(
              contactPhoneId = dpsContactAddressPhoneId,
              contactId = nomisPersonIdAndDpsContactId,
              contactAddressId = dpsContactAddressId,
              phoneNumber = "07973 555 5555",
              phoneType = "MOB",
              extNumber = "x555",
            ),
          )
          nomisApi.stubUpdatePersonAddressPhone(personId = nomisPersonIdAndDpsContactId, addressId = nomisAddressId, phoneId = nomisPhoneId)
          publishUpdateContactAddressPhoneDomainEvent(contactAddressPhoneId = dpsContactAddressPhoneId.toString(), contactAddressId = dpsContactAddressId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-phone-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the phone updated`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-phone-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactAddressPhoneId", dpsContactAddressPhoneId.toString())
              assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get phone details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-address-phone/$dpsContactAddressPhoneId")))
        }

        @Test
        fun `will update the phone in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address/$nomisAddressId/phone/$nomisPhoneId")))
        }

        @Test
        fun `the updated phone will contain details of the DPS contact phone`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("number", "07973 555 5555")
              .withRequestBodyJsonPath("typeCode", "MOB")
              .withRequestBodyJsonPath("extension", "x555"),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-address-phone.deleted")
  inner class ContactAddressPhoneDeleted {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Phone delete")
    inner class WhenNomisDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteContactAddressPhoneDomainEvent(contactAddressPhoneId = "12345", contactAddressId = "65432", contactId = "76556", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-address-phone-delete-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Phone delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactAddressPhoneId = 1234567L
        private val dpsContactAddressId = 9836373L
        private val nomisPhoneId = 7654321L
        private val nomisAddressId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressPhoneIdOrNull(
            dpsContactAddressPhoneId = dpsContactAddressPhoneId,
            PersonPhoneMappingDto(
              dpsId = dpsContactAddressPhoneId.toString(),
              nomisId = nomisPhoneId,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
              mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
            ),
          )
          mappingApi.stubGetByDpsContactAddressIdOrNull(dpsContactAddressId = dpsContactAddressId, PersonAddressMappingDto(dpsId = dpsContactAddressId.toString(), nomisId = nomisAddressId, PersonAddressMappingDto.MappingType.DPS_CREATED))
          nomisApi.stubDeletePersonAddressPhone(personId = nomisPersonIdAndDpsContactId, addressId = nomisAddressId, phoneId = nomisPhoneId)
          mappingApi.stubDeleteByNomisPhoneId(nomisPhoneId = nomisPhoneId)
          publishDeleteContactAddressPhoneDomainEvent(contactAddressPhoneId = dpsContactAddressPhoneId.toString(), contactAddressId = dpsContactAddressId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-phone-delete-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the phone deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-phone-delete-success"),
            check {
              assertThat(it).containsEntry("dpsContactAddressPhoneId", dpsContactAddressPhoneId.toString())
              assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will delete the phone in NOMIS`() {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address/$nomisAddressId/phone/$nomisPhoneId")))
        }

        @Test
        fun `will delete the phone mapping`() {
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")))
        }
      }

      @Nested
      @DisplayName("when phone mapping already deleted")
      inner class PhoneMappingMissing {
        private val dpsContactAddressPhoneId = 1234567L
        private val dpsContactAddressId = 9836373L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId = dpsContactAddressPhoneId, null)
          publishDeleteContactAddressPhoneDomainEvent(contactAddressPhoneId = dpsContactAddressPhoneId.toString(), contactAddressId = dpsContactAddressId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will show deleted skipped`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-phone-delete-skipped"),
            check {
              assertThat(it).containsEntry("dpsContactAddressPhoneId", dpsContactAddressPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("reason", "Phone already deleted")
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when address mapping already deleted")
      inner class AddressMappingMissing {
        private val dpsContactAddressPhoneId = 1234567L
        private val dpsContactAddressId = 9836373L
        private val nomisPhoneId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactAddressPhoneIdOrNull(
            dpsContactAddressPhoneId = dpsContactAddressPhoneId,
            PersonPhoneMappingDto(
              dpsId = dpsContactAddressPhoneId.toString(),
              nomisId = nomisPhoneId,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
              mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
            ),
          )
          mappingApi.stubGetByDpsContactAddressIdOrNull(dpsContactAddressId = dpsContactAddressId, null)
          publishDeleteContactAddressPhoneDomainEvent(contactAddressPhoneId = dpsContactAddressPhoneId.toString(), contactAddressId = dpsContactAddressId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will show deleted skipped`() {
          verify(telemetryClient).trackEvent(
            eq("contact-address-phone-delete-skipped"),
            check {
              assertThat(it).containsEntry("dpsContactAddressPhoneId", dpsContactAddressPhoneId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("reason", "Address already deleted")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.prisoner-contact-restriction.created")
  inner class PrisonerContactRestrictionCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Restriction create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreatePrisonerContactRestrictionDomainEvent(prisonerContactRestrictionId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("prisoner-contact-restriction-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Restriction create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsPrisonerContactRestrictionId = 1234567L
        private val dpsPrisonerContactId = 9836373L
        private val nomisRestrictionId = 7654321L
        private val nomisContactId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId = dpsPrisonerContactRestrictionId, null)
          mappingApi.stubGetByDpsPrisonerContactId(dpsPrisonerContactId = dpsPrisonerContactId, PersonContactMappingDto(dpsId = dpsPrisonerContactId.toString(), nomisId = nomisContactId, PersonContactMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetPrisonerContactRestriction(
            prisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
            prisonerContactRestriction().copy(
              prisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
              contactId = nomisPersonIdAndDpsContactId,
              prisonerContactId = dpsPrisonerContactId,
              restrictionType = "BAN",
              comments = "Banned for life",
              startDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2026-01-01"),
              createdBy = "A.SMITH",
            ),
          )
          nomisApi.stubCreateContactRestriction(personId = nomisPersonIdAndDpsContactId, contactId = nomisContactId, createContactPersonRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreateContactRestrictionMapping()
          publishCreatePrisonerContactRestrictionDomainEvent(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("prisoner-contact-restriction-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the restriction created`() {
          verify(telemetryClient).trackEvent(
            eq("prisoner-contact-restriction-create-success"),
            check {
              assertThat(it).containsEntry("dpsPrisonerContactRestrictionId", dpsPrisonerContactRestrictionId.toString())
              assertThat(it).containsEntry("nomisContactRestrictionId", nomisRestrictionId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("dpsPrisonerContactId", dpsPrisonerContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisContactId", nomisContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get restriction details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/prisoner-contact-restriction/$dpsPrisonerContactRestrictionId")))
        }

        @Test
        fun `will create the restriction in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact/$nomisContactId/restriction")))
        }

        @Test
        fun `the created restriction will contain details of the DPS contact restriction`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("comment", "Banned for life")
              .withRequestBodyJsonPath("typeCode", "BAN")
              .withRequestBodyJsonPath("effectiveDate", "2020-01-01")
              .withRequestBodyJsonPath("expiryDate", "2026-01-01")
              .withRequestBodyJsonPath("enteredStaffUsername", "A.SMITH"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/contact-restriction"))
              .withRequestBodyJsonPath("dpsId", "$dpsPrisonerContactRestrictionId")
              .withRequestBodyJsonPath("nomisId", nomisRestrictionId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsPrisonerContactRestrictionId = 1234567L
        private val dpsPrisonerContactId = 9836373L
        private val nomisRestrictionId = 7654321L
        private val nomisContactId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId = dpsPrisonerContactRestrictionId, null)
          mappingApi.stubGetByDpsPrisonerContactId(dpsPrisonerContactId = dpsPrisonerContactId, PersonContactMappingDto(dpsId = dpsPrisonerContactId.toString(), nomisId = nomisContactId, PersonContactMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetPrisonerContactRestriction(
            prisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
            prisonerContactRestriction().copy(
              prisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
              contactId = nomisPersonIdAndDpsContactId,
              prisonerContactId = dpsPrisonerContactId,
              restrictionType = "BAN",
              comments = "Banned for life",
              startDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2026-01-01"),
              createdBy = "A.SMITH",
            ),
          )
          nomisApi.stubCreateContactRestriction(personId = nomisPersonIdAndDpsContactId, contactId = nomisContactId, createContactPersonRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreateContactRestrictionMappingFollowedBySuccess()
          publishCreatePrisonerContactRestrictionDomainEvent(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("prisoner-contact-restriction-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("prisoner-contact-restriction-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the restriction in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("prisoner-contact-restriction-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact/$nomisContactId/restriction")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsPrisonerContactRestrictionId = 1234567L
        private val dpsPrisonerContactId = 9836373L
        private val nomisRestrictionId = 7654321L
        private val nomisContactId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId = dpsPrisonerContactRestrictionId, null)
          mappingApi.stubGetByDpsPrisonerContactId(dpsPrisonerContactId = dpsPrisonerContactId, PersonContactMappingDto(dpsId = dpsPrisonerContactId.toString(), nomisId = nomisContactId, PersonContactMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetPrisonerContactRestriction(
            prisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
            prisonerContactRestriction().copy(
              prisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
              contactId = nomisPersonIdAndDpsContactId,
              prisonerContactId = dpsPrisonerContactId,
              restrictionType = "BAN",
              comments = "Banned for life",
              startDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2026-01-01"),
              createdBy = "A.SMITH",
            ),
          )
          nomisApi.stubCreateContactRestriction(personId = nomisPersonIdAndDpsContactId, contactId = nomisContactId, createContactPersonRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreateContactRestrictionMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonRestrictionMappingDto(
                  dpsId = dpsPrisonerContactRestrictionId.toString(),
                  nomisId = 999999,
                  mappingType = PersonRestrictionMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonRestrictionMappingDto(
                  dpsId = dpsPrisonerContactRestrictionId.toString(),
                  nomisId = nomisRestrictionId,
                  mappingType = PersonRestrictionMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreatePrisonerContactRestrictionDomainEvent(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-prisoner-contact-restriction-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the restriction in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-prisoner-contact-restriction-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact/$nomisContactId/restriction")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.prisoner-contact-restriction.updated")
  inner class PrisonerContactRestrictionUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Restriction update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdatedPrisonerContactRestrictionDomainEvent(prisonerContactRestrictionId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("prisoner-contact-restriction-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Restriction update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsPrisonerContactRestrictionId = 1234567L
        private val dpsPrisonerContactId = 9836373L
        private val nomisRestrictionId = 7654321L
        private val nomisContactId = 947384L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerContactRestrictionId(
            dpsPrisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
            PersonContactRestrictionMappingDto(
              dpsId = dpsPrisonerContactRestrictionId.toString(),
              nomisId = nomisRestrictionId,
              mappingType = PersonContactRestrictionMappingDto.MappingType.MIGRATED,
            ),
          )
          mappingApi.stubGetByDpsPrisonerContactId(dpsPrisonerContactId = dpsPrisonerContactId, PersonContactMappingDto(dpsId = dpsPrisonerContactId.toString(), nomisId = nomisContactId, PersonContactMappingDto.MappingType.DPS_CREATED))
          dpsApi.stubGetPrisonerContactRestriction(
            prisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
            prisonerContactRestriction().copy(
              prisonerContactRestrictionId = dpsPrisonerContactRestrictionId,
              contactId = nomisPersonIdAndDpsContactId,
              prisonerContactId = dpsPrisonerContactId,
              restrictionType = "BAN",
              comments = "Banned for life",
              startDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2026-01-01"),
              createdBy = "A.TWEEK",
              updatedBy = "A.SMITH",
            ),
          )
          nomisApi.stubUpdateContactRestriction(personId = nomisPersonIdAndDpsContactId, contactId = nomisContactId, contactRestrictionId = nomisRestrictionId)
          publishUpdatedPrisonerContactRestrictionDomainEvent(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("prisoner-contact-restriction-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the restriction updated`() {
          verify(telemetryClient).trackEvent(
            eq("prisoner-contact-restriction-update-success"),
            check {
              assertThat(it).containsEntry("dpsPrisonerContactRestrictionId", dpsPrisonerContactRestrictionId.toString())
              assertThat(it).containsEntry("nomisContactRestrictionId", nomisRestrictionId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("dpsPrisonerContactId", dpsPrisonerContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisContactId", nomisContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get restriction details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/prisoner-contact-restriction/$dpsPrisonerContactRestrictionId")))
        }

        @Test
        fun `will update the restriction in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact/$nomisContactId/restriction/$nomisRestrictionId")))
        }

        @Test
        fun `the update restriction will contain details of the DPS contact restriction`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("comment", "Banned for life")
              .withRequestBodyJsonPath("typeCode", "BAN")
              .withRequestBodyJsonPath("effectiveDate", "2020-01-01")
              .withRequestBodyJsonPath("expiryDate", "2026-01-01")
              .withRequestBodyJsonPath("enteredStaffUsername", "A.SMITH"),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-restriction.created")
  inner class ContactRestrictionCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Restriction create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateContactRestrictionDomainEvent(contactRestrictionId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-restriction-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Restriction create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactRestrictionId = 1234567L
        private val nomisRestrictionId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactRestrictionIdOrNull(dpsContactRestrictionId = dpsContactRestrictionId, null)
          dpsApi.stubGetContactRestriction(
            contactRestrictionId = dpsContactRestrictionId,
            contactRestriction().copy(
              contactRestrictionId = dpsContactRestrictionId,
              contactId = nomisPersonIdAndDpsContactId,
              restrictionType = "BAN",
              comments = "Banned for life",
              startDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2026-01-01"),
              createdBy = "T.SWIFT",
            ),
          )
          nomisApi.stubCreatePersonRestriction(personId = nomisPersonIdAndDpsContactId, createContactPersonRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreatePersonRestrictionMapping()
          publishCreateContactRestrictionDomainEvent(contactRestrictionId = dpsContactRestrictionId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-restriction-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the contact created`() {
          verify(telemetryClient).trackEvent(
            eq("contact-restriction-create-success"),
            check {
              assertThat(it).containsEntry("dpsContactRestrictionId", dpsContactRestrictionId.toString())
              assertThat(it).containsEntry("nomisPersonRestrictionId", nomisRestrictionId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get restriction details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-restriction/$dpsContactRestrictionId")))
        }

        @Test
        fun `will create the restriction in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/restriction")))
        }

        @Test
        fun `the created restriction will contain details of the DPS contact restriction`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("typeCode", "BAN")
              .withRequestBodyJsonPath("comment", "Banned for life")
              .withRequestBodyJsonPath("enteredStaffUsername", "T.SWIFT")
              .withRequestBodyJsonPath("effectiveDate", "2020-01-01")
              .withRequestBodyJsonPath("expiryDate", "2026-01-01"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/person-restriction"))
              .withRequestBodyJsonPath("dpsId", "$dpsContactRestrictionId")
              .withRequestBodyJsonPath("nomisId", nomisRestrictionId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsContactRestrictionId = 1234567L
        private val nomisRestrictionId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactRestrictionIdOrNull(dpsContactRestrictionId = dpsContactRestrictionId, null)
          dpsApi.stubGetContactRestriction(
            contactRestrictionId = dpsContactRestrictionId,
            contactRestriction().copy(
              contactRestrictionId = dpsContactRestrictionId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonRestriction(personId = nomisPersonIdAndDpsContactId, createContactPersonRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreatePersonRestrictionMappingFollowedBySuccess()
          publishCreateContactRestrictionDomainEvent(contactRestrictionId = dpsContactRestrictionId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-restriction-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-restriction-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the restriction in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-restriction-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/restriction")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsContactRestrictionId = 1234567L
        private val nomisRestrictionId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactRestrictionIdOrNull(dpsContactRestrictionId = dpsContactRestrictionId, null)
          dpsApi.stubGetContactRestriction(
            contactRestrictionId = dpsContactRestrictionId,
            contactRestriction().copy(
              contactRestrictionId = dpsContactRestrictionId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonRestriction(nomisPersonIdAndDpsContactId, createContactPersonRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreatePersonRestrictionMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonRestrictionMappingDto(
                  dpsId = dpsContactRestrictionId.toString(),
                  nomisId = 999999,
                  mappingType = PersonRestrictionMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonRestrictionMappingDto(
                  dpsId = dpsContactRestrictionId.toString(),
                  nomisId = nomisRestrictionId,
                  mappingType = PersonRestrictionMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateContactRestrictionDomainEvent(contactRestrictionId = dpsContactRestrictionId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-restriction-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the restriction in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-restriction-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/restriction")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-restriction.updated")
  inner class ContactRestrictionUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Restriction update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdatedContactRestrictionDomainEvent(contactRestrictionId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-restriction-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Restriction update")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactRestrictionId = 1234567L
        private val nomisRestrictionId = 7654321L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactRestrictionId(
            dpsContactRestrictionId = dpsContactRestrictionId,
            PersonRestrictionMappingDto(
              dpsId = dpsContactRestrictionId.toString(),
              nomisId = nomisRestrictionId,
              mappingType = PersonRestrictionMappingDto.MappingType.MIGRATED,
            ),
          )
          dpsApi.stubGetContactRestriction(
            contactRestrictionId = dpsContactRestrictionId,
            contactRestriction().copy(
              contactRestrictionId = dpsContactRestrictionId,
              contactId = nomisPersonIdAndDpsContactId,
              restrictionType = "BAN",
              comments = "Banned for life",
              startDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2026-01-01"),
              createdBy = "T.FREE",
              updatedBy = "T.SWIFT",
            ),
          )
          nomisApi.stubUpdatePersonRestriction(personId = nomisPersonIdAndDpsContactId, personRestrictionId = nomisRestrictionId)
          publishUpdatedContactRestrictionDomainEvent(contactRestrictionId = dpsContactRestrictionId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-restriction-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the contact updated`() {
          verify(telemetryClient).trackEvent(
            eq("contact-restriction-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactRestrictionId", dpsContactRestrictionId.toString())
              assertThat(it).containsEntry("nomisPersonRestrictionId", nomisRestrictionId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get restriction details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-restriction/$dpsContactRestrictionId")))
        }

        @Test
        fun `will update the restriction in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/restriction/$nomisRestrictionId")))
        }

        @Test
        fun `the update restriction will contain details of the DPS contact restriction`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("typeCode", "BAN")
              .withRequestBodyJsonPath("comment", "Banned for life")
              .withRequestBodyJsonPath("enteredStaffUsername", "T.SWIFT")
              .withRequestBodyJsonPath("effectiveDate", "2020-01-01")
              .withRequestBodyJsonPath("expiryDate", "2026-01-01"),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-identity.created")
  inner class ContactIdentityCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Identity create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateContactIdentityDomainEvent(contactIdentityId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-identity-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Identity create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactIdentityId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdentityIdOrNull(dpsContactIdentityId = dpsContactIdentityId, null)
          dpsApi.stubGetContactIdentity(
            contactIdentityId = dpsContactIdentityId,
            contactIdentity().copy(
              contactIdentityId = dpsContactIdentityId,
              contactId = nomisPersonIdAndDpsContactId,
              identityValue = "SMIT63636DL",
              identityType = "DL",
              issuingAuthority = "DVLA",
            ),
          )
          nomisApi.stubCreatePersonIdentifier(personId = nomisPersonIdAndDpsContactId, createPersonIdentifierResponse().copy(sequence = nomisSequenceNumber))
          mappingApi.stubCreateIdentifierMapping()
          publishCreateContactIdentityDomainEvent(contactIdentityId = dpsContactIdentityId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-identity-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the identity created`() {
          verify(telemetryClient).trackEvent(
            eq("contact-identity-create-success"),
            check {
              assertThat(it).containsEntry("dpsContactIdentityId", dpsContactIdentityId.toString())
              assertThat(it).containsEntry("nomisSequenceNumber", nomisSequenceNumber.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get identity details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-identity/$dpsContactIdentityId")))
        }

        @Test
        fun `will create the identifier in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/identifier")))
        }

        @Test
        fun `the created identifier will contain details of the DPS contact identity`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("identifier", "SMIT63636DL")
              .withRequestBodyJsonPath("typeCode", "DL")
              .withRequestBodyJsonPath("issuedAuthority", "DVLA"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/identifier"))
              .withRequestBodyJsonPath("dpsId", "$dpsContactIdentityId")
              .withRequestBodyJsonPath("nomisSequenceNumber", nomisSequenceNumber)
              .withRequestBodyJsonPath("nomisPersonId", nomisPersonIdAndDpsContactId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsContactIdentityId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdentityIdOrNull(dpsContactIdentityId = dpsContactIdentityId, null)
          dpsApi.stubGetContactIdentity(
            contactIdentityId = dpsContactIdentityId,
            contactIdentity().copy(
              contactIdentityId = dpsContactIdentityId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonIdentifier(personId = nomisPersonIdAndDpsContactId, createPersonIdentifierResponse().copy(sequence = nomisSequenceNumber))
          mappingApi.stubCreateIdentifierMappingFollowedBySuccess()
          publishCreateContactIdentityDomainEvent(contactIdentityId = dpsContactIdentityId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-identity-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-identity-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the identity in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-identity-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/identifier")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsContactIdentityId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdentityIdOrNull(dpsContactIdentityId = dpsContactIdentityId, null)
          dpsApi.stubGetContactIdentity(
            contactIdentityId = dpsContactIdentityId,
            contactIdentity().copy(
              contactIdentityId = dpsContactIdentityId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonIdentifier(nomisPersonIdAndDpsContactId, createPersonIdentifierResponse().copy(sequence = nomisSequenceNumber))
          mappingApi.stubCreateIdentifierMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonIdentifierMappingDto(
                  dpsId = dpsContactIdentityId.toString(),
                  nomisPersonId = nomisPersonIdAndDpsContactId,
                  nomisSequenceNumber = 5,
                  mappingType = PersonIdentifierMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonIdentifierMappingDto(
                  dpsId = dpsContactIdentityId.toString(),
                  nomisPersonId = nomisPersonIdAndDpsContactId,
                  nomisSequenceNumber = nomisSequenceNumber,
                  mappingType = PersonIdentifierMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateContactIdentityDomainEvent(contactIdentityId = dpsContactIdentityId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-identity-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the identity in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-identity-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/identifier")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-identity.updated")
  inner class ContactIdentityUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Identity update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateContactIdentityDomainEvent(contactIdentityId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-identity-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Identity update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactIdentityId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdentityId(
            dpsContactIdentityId = dpsContactIdentityId,
            PersonIdentifierMappingDto(
              dpsId = dpsContactIdentityId.toString(),
              nomisPersonId = nomisPersonIdAndDpsContactId,
              nomisSequenceNumber = nomisSequenceNumber,
              mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
            ),
          )
          dpsApi.stubGetContactIdentity(
            contactIdentityId = dpsContactIdentityId,
            contactIdentity().copy(
              contactIdentityId = dpsContactIdentityId,
              contactId = nomisPersonIdAndDpsContactId,
              identityValue = "SMIT63636DL",
              identityType = "DL",
              issuingAuthority = "DVLA",
            ),
          )
          nomisApi.stubUpdatePersonIdentifier(personId = nomisPersonIdAndDpsContactId, sequence = nomisSequenceNumber)
          publishUpdateContactIdentityDomainEvent(contactIdentityId = dpsContactIdentityId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("contact-identity-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the identity updated`() {
          verify(telemetryClient).trackEvent(
            eq("contact-identity-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactIdentityId", dpsContactIdentityId.toString())
              assertThat(it).containsEntry("nomisSequenceNumber", nomisSequenceNumber.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get identity details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/contact-identity/$dpsContactIdentityId")))
        }

        @Test
        fun `will update the identifier in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/identifier/$nomisSequenceNumber")))
        }

        @Test
        fun `the updated identifier will contain details of the DPS contact identity`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("identifier", "SMIT63636DL")
              .withRequestBodyJsonPath("typeCode", "DL")
              .withRequestBodyJsonPath("issuedAuthority", "DVLA"),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-identity.deleted")
  inner class ContactIdentityDeleted {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Identity delete")
    inner class WhenNomisDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteContactIdentityDomainEvent(contactIdentityId = "12345", source = "NOMIS", contactId = "38383")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-identity-delete-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Identity delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactIdentityId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdentityIdOrNull(
            dpsContactIdentityId = dpsContactIdentityId,
            PersonIdentifierMappingDto(
              dpsId = dpsContactIdentityId.toString(),
              nomisPersonId = nomisPersonIdAndDpsContactId,
              nomisSequenceNumber = nomisSequenceNumber,
              mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
            ),
          )
          nomisApi.stubDeletePersonIdentifier(personId = nomisPersonIdAndDpsContactId, sequence = nomisSequenceNumber)
          mappingApi.stubDeleteByNomisIdentifierIds(nomisPersonId = nomisPersonIdAndDpsContactId, nomisSequenceNumber = nomisSequenceNumber)
          publishDeleteContactIdentityDomainEvent(contactIdentityId = dpsContactIdentityId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("contact-identity-delete-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the identity deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-identity-delete-success"),
            check {
              assertThat(it).containsEntry("dpsContactIdentityId", dpsContactIdentityId.toString())
              assertThat(it).containsEntry("nomisSequenceNumber", nomisSequenceNumber.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will delete the identity in NOMIS`() {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/identifier/$nomisSequenceNumber")))
        }

        @Test
        fun `will delete the identifier mapping`() {
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier/nomis-person-id/$nomisPersonIdAndDpsContactId/nomis-sequence-number/$nomisSequenceNumber")))
        }
      }

      @Nested
      @DisplayName("Identity mapping already deleted")
      inner class IdentityMappingMissing {
        private val dpsContactIdentityId = 1234567L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactIdentityIdOrNull(dpsContactIdentityId = dpsContactIdentityId, null)
          publishDeleteContactIdentityDomainEvent(contactIdentityId = dpsContactIdentityId.toString(), contactId = nomisPersonIdAndDpsContactId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `telemetry will contain key facts about the identity deleted`() {
          verify(telemetryClient).trackEvent(
            eq("contact-identity-delete-skipped"),
            check {
              assertThat(it).containsEntry("dpsContactIdentityId", dpsContactIdentityId.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-employment.created")
  inner class ContactEmploymentCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Employment create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateContactEmploymentDomainEvent(contactEmploymentId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-employment-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Employment create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactEmploymentId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmploymentIdOrNull(dpsContactEmploymentId = dpsContactEmploymentId, null)
          dpsApi.stubGetContactEmployment(
            contactEmploymentId = dpsContactEmploymentId,
            contactEmployment().copy(
              organisationId = 87654,
              contactId = nomisPersonIdAndDpsContactId,
              employmentId = dpsContactEmploymentId,
              active = true,
            ),
          )
          nomisApi.stubCreatePersonEmployment(personId = nomisPersonIdAndDpsContactId, createPersonEmploymentResponse().copy(sequence = nomisSequenceNumber))
          mappingApi.stubCreateEmploymentMapping()
          publishCreateContactEmploymentDomainEvent(contactEmploymentId = dpsContactEmploymentId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("contact-employment-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the employment created`() {
          verify(telemetryClient).trackEvent(
            eq("contact-employment-create-success"),
            check {
              assertThat(it).containsEntry("dpsContactEmploymentId", dpsContactEmploymentId.toString())
              assertThat(it).containsEntry("nomisSequenceNumber", nomisSequenceNumber.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get employment details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/employment/$dpsContactEmploymentId")))
        }

        @Test
        fun `will create the employment in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/employment")))
        }

        @Test
        fun `the created employment will contain details of the DPS contact employment`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("corporateId", "87654")
              .withRequestBodyJsonPath("active", "true"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/employment"))
              .withRequestBodyJsonPath("dpsId", "$dpsContactEmploymentId")
              .withRequestBodyJsonPath("nomisSequenceNumber", nomisSequenceNumber)
              .withRequestBodyJsonPath("nomisPersonId", nomisPersonIdAndDpsContactId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsContactEmploymentId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmploymentIdOrNull(dpsContactEmploymentId = dpsContactEmploymentId, null)
          dpsApi.stubGetContactEmployment(
            contactEmploymentId = dpsContactEmploymentId,
            contactEmployment().copy(
              employmentId = dpsContactEmploymentId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonEmployment(personId = nomisPersonIdAndDpsContactId, createPersonEmploymentResponse().copy(sequence = nomisSequenceNumber))
          mappingApi.stubCreateEmploymentMappingFollowedBySuccess()
          publishCreateContactEmploymentDomainEvent(contactEmploymentId = dpsContactEmploymentId.toString())
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-employment-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-employment-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the employment in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("contact-employment-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/employment")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsContactEmploymentId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmploymentIdOrNull(dpsContactEmploymentId = dpsContactEmploymentId, null)
          dpsApi.stubGetContactEmployment(
            contactEmploymentId = dpsContactEmploymentId,
            contactEmployment().copy(
              employmentId = dpsContactEmploymentId,
              contactId = nomisPersonIdAndDpsContactId,
            ),
          )
          nomisApi.stubCreatePersonEmployment(nomisPersonIdAndDpsContactId, createPersonEmploymentResponse().copy(sequence = nomisSequenceNumber))
          mappingApi.stubCreateEmploymentMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PersonEmploymentMappingDto(
                  dpsId = dpsContactEmploymentId.toString(),
                  nomisPersonId = nomisPersonIdAndDpsContactId,
                  nomisSequenceNumber = 5,
                  mappingType = PersonEmploymentMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PersonEmploymentMappingDto(
                  dpsId = dpsContactEmploymentId.toString(),
                  nomisPersonId = nomisPersonIdAndDpsContactId,
                  nomisSequenceNumber = nomisSequenceNumber,
                  mappingType = PersonEmploymentMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateContactEmploymentDomainEvent(contactEmploymentId = dpsContactEmploymentId.toString())
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-employment-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the employment in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-contact-employment-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/employment")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("contacts-api.contact-employment.updated")
  inner class ContactEmploymentUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Contact Employment update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateContactEmploymentDomainEvent(contactEmploymentId = "12345", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("contact-employment-update-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Contact Employment update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsContactEmploymentId = 1234567L
        private val nomisSequenceNumber = 4L
        private val nomisPersonIdAndDpsContactId = 54321L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsContactEmploymentId(
            dpsContactEmploymentId = dpsContactEmploymentId,
            PersonEmploymentMappingDto(
              dpsId = dpsContactEmploymentId.toString(),
              nomisPersonId = nomisPersonIdAndDpsContactId,
              nomisSequenceNumber = nomisSequenceNumber,
              mappingType = PersonEmploymentMappingDto.MappingType.MIGRATED,
            ),
          )
          dpsApi.stubGetContactEmployment(
            contactEmploymentId = dpsContactEmploymentId,
            contactEmployment().copy(
              organisationId = 54321,
              contactId = nomisPersonIdAndDpsContactId,
              active = false,
            ),
          )
          nomisApi.stubUpdatePersonEmployment(personId = nomisPersonIdAndDpsContactId, sequence = nomisSequenceNumber)
          publishUpdateContactEmploymentDomainEvent(contactEmploymentId = dpsContactEmploymentId.toString())
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("contact-employment-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the employment updated`() {
          verify(telemetryClient).trackEvent(
            eq("contact-employment-update-success"),
            check {
              assertThat(it).containsEntry("dpsContactEmploymentId", dpsContactEmploymentId.toString())
              assertThat(it).containsEntry("nomisSequenceNumber", nomisSequenceNumber.toString())
              assertThat(it).containsEntry("dpsContactId", nomisPersonIdAndDpsContactId.toString())
              assertThat(it).containsEntry("nomisPersonId", nomisPersonIdAndDpsContactId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get employment details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/employment/$dpsContactEmploymentId")))
        }

        @Test
        fun `will update the employment in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/employment/$nomisSequenceNumber")))
        }

        @Test
        fun `the updated employment will contain details of the DPS contact employment`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("corporateId", "54321")
              .withRequestBodyJsonPath("active", "false"),
          )
        }
      }
    }
  }

  private fun publishCreateContactDomainEvent(contactId: String, source: String = "DPS") {
    with("contacts-api.contact.created") {
      publishDomainEvent(eventType = this, payload = contactMessagePayload(eventType = this, contactId = contactId, source = source))
    }
  }

  private fun publishUpdateContactDomainEvent(contactId: String, source: String = "DPS") {
    with("contacts-api.contact.updated") {
      publishDomainEvent(eventType = this, payload = contactMessagePayload(eventType = this, contactId = contactId, source = source))
    }
  }

  private fun publishDeleteContactDomainEvent(contactId: String, source: String = "DPS") {
    with("contacts-api.contact.deleted") {
      publishDomainEvent(eventType = this, payload = contactMessagePayload(eventType = this, contactId = contactId, source = source))
    }
  }

  private fun publishCreatePrisonerContactDomainEvent(prisonerContactId: String, source: String = "DPS") {
    with("contacts-api.prisoner-contact.created") {
      publishDomainEvent(eventType = this, payload = prisonerContactMessagePayload(eventType = this, prisonerContactId = prisonerContactId, source = source))
    }
  }
  private fun publishUpdatePrisonerContactDomainEvent(prisonerContactId: String, source: String = "DPS") {
    with("contacts-api.prisoner-contact.updated") {
      publishDomainEvent(eventType = this, payload = prisonerContactMessagePayload(eventType = this, prisonerContactId = prisonerContactId, source = source))
    }
  }

  private fun publishCreatePrisonerContactRestrictionDomainEvent(prisonerContactRestrictionId: String, source: String = "DPS") {
    with("contacts-api.prisoner-contact-restriction.created") {
      publishDomainEvent(eventType = this, payload = prisonerContactRestrictionMessagePayload(eventType = this, prisonerContactRestrictionId = prisonerContactRestrictionId, source = source))
    }
  }
  private fun publishUpdatedPrisonerContactRestrictionDomainEvent(prisonerContactRestrictionId: String, source: String = "DPS") {
    with("contacts-api.prisoner-contact-restriction.updated") {
      publishDomainEvent(eventType = this, payload = prisonerContactRestrictionMessagePayload(eventType = this, prisonerContactRestrictionId = prisonerContactRestrictionId, source = source))
    }
  }

  private fun publishCreateContactRestrictionDomainEvent(contactRestrictionId: String, source: String = "DPS") {
    with("contacts-api.contact-restriction.created") {
      publishDomainEvent(eventType = this, payload = contactRestrictionMessagePayload(eventType = this, contactRestrictionId = contactRestrictionId, source = source))
    }
  }

  private fun publishUpdatedContactRestrictionDomainEvent(contactRestrictionId: String, source: String = "DPS") {
    with("contacts-api.contact-restriction.updated") {
      publishDomainEvent(eventType = this, payload = contactRestrictionMessagePayload(eventType = this, contactRestrictionId = contactRestrictionId, source = source))
    }
  }

  private fun publishCreateContactAddressDomainEvent(contactAddressId: String, source: String = "DPS") {
    with("contacts-api.contact-address.created") {
      publishDomainEvent(eventType = this, payload = contactAddressMessagePayload(eventType = this, contactAddressId = contactAddressId, source = source))
    }
  }
  private fun publishUpdateContactAddressDomainEvent(contactAddressId: String, source: String = "DPS") {
    with("contacts-api.contact-address.updated") {
      publishDomainEvent(eventType = this, payload = contactAddressMessagePayload(eventType = this, contactAddressId = contactAddressId, source = source))
    }
  }
  private fun publishDeleteContactAddressDomainEvent(contactAddressId: String, contactId: String, source: String = "DPS") {
    with("contacts-api.contact-address.deleted") {
      publishDomainEvent(eventType = this, payload = contactAddressMessagePayload(eventType = this, contactAddressId = contactAddressId, contactId = contactId, source = source))
    }
  }

  private fun publishCreateContactEmailDomainEvent(contactEmailId: String, source: String = "DPS") {
    with("contacts-api.contact-email.created") {
      publishDomainEvent(eventType = this, payload = contactEmailMessagePayload(eventType = this, contactEmailId = contactEmailId, source = source))
    }
  }
  private fun publishUpdateContactEmailDomainEvent(contactEmailId: String, source: String = "DPS") {
    with("contacts-api.contact-email.updated") {
      publishDomainEvent(eventType = this, payload = contactEmailMessagePayload(eventType = this, contactEmailId = contactEmailId, source = source))
    }
  }
  private fun publishDeleteContactEmailDomainEvent(contactEmailId: String, contactId: String, source: String = "DPS") {
    with("contacts-api.contact-email.deleted") {
      publishDomainEvent(eventType = this, payload = contactEmailMessagePayload(eventType = this, contactEmailId = contactEmailId, contactId = contactId, source = source))
    }
  }

  private fun publishCreateContactPhoneDomainEvent(contactPhoneId: String, source: String = "DPS") {
    with("contacts-api.contact-phone.created") {
      publishDomainEvent(eventType = this, payload = contactPhoneMessagePayload(eventType = this, contactPhoneId = contactPhoneId, source = source))
    }
  }
  private fun publishUpdateContactPhoneDomainEvent(contactPhoneId: String, source: String = "DPS") {
    with("contacts-api.contact-phone.updated") {
      publishDomainEvent(eventType = this, payload = contactPhoneMessagePayload(eventType = this, contactPhoneId = contactPhoneId, source = source))
    }
  }
  private fun publishDeleteContactPhoneDomainEvent(contactPhoneId: String, contactId: String, source: String = "DPS") {
    with("contacts-api.contact-phone.deleted") {
      publishDomainEvent(eventType = this, payload = contactPhoneMessagePayload(eventType = this, contactPhoneId = contactPhoneId, contactId = contactId, source = source))
    }
  }
  private fun publishCreateContactAddressPhoneDomainEvent(contactAddressPhoneId: String, contactAddressId: String, source: String = "DPS") {
    with("contacts-api.contact-address-phone.created") {
      publishDomainEvent(eventType = this, payload = contactAddressPhoneMessagePayload(eventType = this, contactAddressPhoneId = contactAddressPhoneId, contactAddressId = contactAddressId, source = source))
    }
  }
  private fun publishUpdateContactAddressPhoneDomainEvent(contactAddressPhoneId: String, contactAddressId: String, source: String = "DPS") {
    with("contacts-api.contact-address-phone.updated") {
      publishDomainEvent(eventType = this, payload = contactAddressPhoneMessagePayload(eventType = this, contactAddressPhoneId = contactAddressPhoneId, contactAddressId = contactAddressId, source = source))
    }
  }
  private fun publishDeleteContactAddressPhoneDomainEvent(contactAddressPhoneId: String, contactAddressId: String, contactId: String, source: String = "DPS") {
    with("contacts-api.contact-address-phone.deleted") {
      publishDomainEvent(eventType = this, payload = contactAddressPhoneMessagePayload(eventType = this, contactAddressPhoneId = contactAddressPhoneId, contactAddressId = contactAddressId, contactId = contactId, source = source))
    }
  }
  private fun publishCreateContactIdentityDomainEvent(contactIdentityId: String, source: String = "DPS") {
    with("contacts-api.contact-identity.created") {
      publishDomainEvent(eventType = this, payload = contactIdentityMessagePayload(eventType = this, contactIdentityId = contactIdentityId, source = source))
    }
  }
  private fun publishUpdateContactIdentityDomainEvent(contactIdentityId: String, source: String = "DPS") {
    with("contacts-api.contact-identity.updated") {
      publishDomainEvent(eventType = this, payload = contactIdentityMessagePayload(eventType = this, contactIdentityId = contactIdentityId, source = source))
    }
  }
  private fun publishDeleteContactIdentityDomainEvent(contactIdentityId: String, contactId: String, source: String = "DPS") {
    with("contacts-api.contact-identity.deleted") {
      publishDomainEvent(eventType = this, payload = contactIdentityMessagePayload(eventType = this, contactIdentityId = contactIdentityId, contactId = contactId, source = source))
    }
  }

  private fun publishCreateContactEmploymentDomainEvent(contactEmploymentId: String, source: String = "DPS") {
    with("contacts-api.employment.created") {
      publishDomainEvent(eventType = this, payload = contactEmploymentMessagePayload(eventType = this, employmentId = contactEmploymentId, source = source))
    }
  }

  private fun publishUpdateContactEmploymentDomainEvent(contactEmploymentId: String, source: String = "DPS") {
    with("contacts-api.employment.updated") {
      publishDomainEvent(eventType = this, payload = contactEmploymentMessagePayload(eventType = this, employmentId = contactEmploymentId, source = source))
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
) = //language=JSON
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
) = //language=JSON
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

fun prisonerContactRestrictionMessagePayload(
  eventType: String,
  prisonerContactRestrictionId: String,
  source: String = "DPS",
  contactId: String = "87654",
  offenderNo: String = "",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "prisonerContactRestrictionId": "$prisonerContactRestrictionId",
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

fun contactRestrictionMessagePayload(
  eventType: String,
  contactRestrictionId: String,
  source: String = "DPS",
  contactId: String = "87654",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "contactRestrictionId": "$contactRestrictionId",
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

fun contactAddressMessagePayload(
  eventType: String,
  contactAddressId: String,
  source: String = "DPS",
  contactId: String = "87654",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "contactAddressId": "$contactAddressId",
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

fun contactEmailMessagePayload(
  eventType: String,
  contactEmailId: String,
  source: String = "DPS",
  contactId: String = "87654",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "contactEmailId": "$contactEmailId",
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

fun contactPhoneMessagePayload(
  eventType: String,
  contactPhoneId: String,
  source: String = "DPS",
  contactId: String = "87654",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "contactPhoneId": "$contactPhoneId",
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

fun contactAddressPhoneMessagePayload(
  eventType: String,
  contactAddressPhoneId: String,
  contactAddressId: String,
  source: String = "DPS",
  contactId: String = "87654",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "contactAddressPhoneId": "$contactAddressPhoneId",
        "contactAddressId": "$contactAddressId",
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

fun contactIdentityMessagePayload(
  eventType: String,
  contactIdentityId: String,
  source: String = "DPS",
  contactId: String = "87654",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "contactIdentityId": "$contactIdentityId",
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

fun contactEmploymentMessagePayload(
  eventType: String,
  employmentId: String,
  source: String = "DPS",
  contactId: String = "87654",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "employmentId": "$employmentId",
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
