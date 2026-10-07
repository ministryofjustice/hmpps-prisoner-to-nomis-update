package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension.Companion.corePersonCprApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath

class CorePersonContactToNomisIntTest(
  @Autowired private val nomisApi: CorePersonNomisApiMockServer,
  @Autowired private val mappingApi: CorePersonContactMappingApiMockServer,
) : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("core-person-record.prison.contact.created")
  inner class ContactCreated {
    private val prisonNumber = "A1234BC"
    private val cprContactId = "11111111-2222-3333-4444-555555555555"
    private val rootOffenderId = 12345L
    private val nomisId = 54321L

    @Nested
    @DisplayName("when NOMIS is the origin of a contact create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishContactCreatedDomainEvent(prisonNumber, cprContactId, source = "nomis")
        waitForAnyProcessingToComplete("core-person-contact-create-ignored")
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("core-person-contact-create-ignored"),
          check {
            assertThat(it).containsEntry("prisonNumber", prisonNumber)
            assertThat(it).containsEntry("cprContactId", cprContactId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when CPR is the origin of a contact create")
    inner class WhenCprCreated {
      @Nested
      @DisplayName("when mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(cprContactId)

          publishContactCreatedDomainEvent(prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-create-duplicate")
        }

        @Test
        fun `will not create the contact in NOMIS`() {
          nomisApi.verify(0, postRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/phone")))
          nomisApi.verify(0, postRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/email")))
        }
      }

      @Nested
      @DisplayName("when contact is a phone number")
      inner class HappyPathPhone {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(cprContactId, mapping = null)
          corePersonCprApi.stubGetPrisonerContact(
            prisonNumber,
            cprContactId,
            prisonerContact(prisonNumber).copy(type = PrisonContact.Type.MOBILE, value = "07700 900000", extension = "123"),
          )
          nomisApi.stubGetPrisonerDetails(prisonNumber, rootOffenderId = rootOffenderId)
          nomisApi.stubCreateOffenderPhone(rootOffenderId, phoneId = nomisId)
          mappingApi.stubCreateContactMapping()

          publishContactCreatedDomainEvent(prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-create-success")
        }

        @Test
        fun `will call CPR to get the contact details`() {
          corePersonCprApi.verify(
            getRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/contact/$cprContactId")),
          )
        }

        @Test
        fun `will create the phone in NOMIS against the root offender`() {
          nomisApi.verify(
            postRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/phone"))
              .withRequestBodyJsonPath("number", "07700 900000")
              .withRequestBodyJsonPath("extension", "123")
              .withRequestBodyJsonPath("typeCode", "MOB"),
          )
        }

        @Test
        fun `will create a mapping for the phone`() {
          mappingApi.verify(
            postRequestedFor(urlPathEqualTo("/mapping/core-person/contact"))
              .withRequestBodyJsonPath("cprId", cprContactId)
              .withRequestBodyJsonPath("nomisId", nomisId)
              .withRequestBodyJsonPath("nomisContactType", "PHONE")
              .withRequestBodyJsonPath("nomisPrisonNumber", prisonNumber)
              .withRequestBodyJsonPath("mappingType", "CPR_CREATED"),
          )
        }

        @Test
        fun `will send success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-contact-create-success"),
            check {
              assertThat(it).containsEntry("prisonNumber", prisonNumber)
              assertThat(it).containsEntry("cprContactId", cprContactId)
              assertThat(it).containsEntry("rootOffenderId", rootOffenderId.toString())
              assertThat(it).containsEntry("nomisContactType", "PHONE")
              assertThat(it).containsEntry("nomisId", nomisId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when contact is a phone number associated with an address")
      inner class HappyPathAddressPhone {
        private val cprAddressId = "22222222-3333-4444-5555-666666666666"
        private val nomisAddressId = 67890L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(cprContactId, mapping = null)
          mappingApi.stubGetAddressMapping(
            cprAddressId,
            mapping = CorePersonAddressMappingDto(
              cprId = cprAddressId,
              nomisId = nomisAddressId,
              nomisPrisonNumber = prisonNumber,
              mappingType = CorePersonAddressMappingDto.MappingType.CPR_CREATED,
            ),
          )
          corePersonCprApi.stubGetPrisonerContact(
            prisonNumber,
            cprContactId,
            prisonerContact(prisonNumber).copy(
              type = PrisonContact.Type.MOBILE,
              value = "07700 900000",
              extension = "123",
              cprAddressId = cprAddressId,
            ),
          )
          nomisApi.stubGetPrisonerDetails(prisonNumber, rootOffenderId = rootOffenderId)
          nomisApi.stubCreateOffenderAddressPhone(rootOffenderId, nomisAddressId, phoneId = nomisId)
          mappingApi.stubCreateContactMapping()

          publishContactCreatedDomainEvent(prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-create-success")
        }

        @Test
        fun `will look up the NOMIS address mapping for the CPR address`() {
          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/core-person/address/cpr-address-id/$cprAddressId")),
          )
        }

        @Test
        fun `will create the phone in NOMIS against the mapped address`() {
          nomisApi.verify(
            postRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/address/$nomisAddressId/phone"))
              .withRequestBodyJsonPath("number", "07700 900000")
              .withRequestBodyJsonPath("extension", "123")
              .withRequestBodyJsonPath("typeCode", "MOB"),
          )
        }

        @Test
        fun `will create the contact mapping for the phone`() {
          mappingApi.verify(
            postRequestedFor(urlPathEqualTo("/mapping/core-person/contact"))
              .withRequestBodyJsonPath("cprId", cprContactId)
              .withRequestBodyJsonPath("nomisId", nomisId)
              .withRequestBodyJsonPath("nomisContactType", "PHONE")
              .withRequestBodyJsonPath("nomisPrisonNumber", prisonNumber)
              .withRequestBodyJsonPath("mappingType", "CPR_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when contact is an email address")
      inner class HappyPathEmail {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(cprContactId, mapping = null)
          corePersonCprApi.stubGetPrisonerContact(
            prisonNumber,
            cprContactId,
            prisonerContact(prisonNumber).copy(type = PrisonContact.Type.EMAIL, value = "test@justice.gov.uk"),
          )
          nomisApi.stubGetPrisonerDetails(prisonNumber, rootOffenderId = rootOffenderId)
          nomisApi.stubCreateOffenderEmail(rootOffenderId, emailAddressId = nomisId)
          mappingApi.stubCreateContactMapping()

          publishContactCreatedDomainEvent(prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-create-success")
        }

        @Test
        fun `will create the email in NOMIS against the root offender`() {
          nomisApi.verify(
            postRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/email"))
              .withRequestBodyJsonPath("email", "test@justice.gov.uk"),
          )
        }

        @Test
        fun `will create a mapping for the email`() {
          mappingApi.verify(
            postRequestedFor(urlPathEqualTo("/mapping/core-person/contact"))
              .withRequestBodyJsonPath("cprId", cprContactId)
              .withRequestBodyJsonPath("nomisId", nomisId)
              .withRequestBodyJsonPath("nomisContactType", "EMAIL")
              .withRequestBodyJsonPath("nomisPrisonNumber", prisonNumber)
              .withRequestBodyJsonPath("mappingType", "CPR_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(cprContactId, mapping = null)
          corePersonCprApi.stubGetPrisonerContact(
            prisonNumber,
            cprContactId,
            prisonerContact(prisonNumber).copy(type = PrisonContact.Type.EMAIL, value = "test@justice.gov.uk"),
          )
          nomisApi.stubGetPrisonerDetails(prisonNumber, rootOffenderId = rootOffenderId)
          nomisApi.stubCreateOffenderEmail(rootOffenderId, emailAddressId = nomisId)
          mappingApi.stubCreateContactMappingFollowedBySuccess()

          publishContactCreatedDomainEvent(prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-create-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-contact-mapping-create-failed"),
            check {
              assertThat(it).containsEntry("cprContactId", cprContactId)
            },
            isNull(),
          )
        }

        @Test
        fun `will create the email in NOMIS once`() {
          nomisApi.verify(1, postRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/email")))
        }

        @Test
        fun `will try to create the mapping twice`() {
          mappingApi.verify(2, postRequestedFor(urlPathEqualTo("/mapping/core-person/contact")))
        }

        @Test
        fun `will eventually send success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-contact-create-success"),
            check {
              assertThat(it).containsEntry("cprContactId", cprContactId)
              assertThat(it).containsEntry("nomisId", nomisId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("core-person-record.prison.contact.updated")
  inner class ContactUpdated {
    private val prisonNumber = "A1234BC"
    private val cprContactId = "11111111-2222-3333-4444-555555555555"
    private val rootOffenderId = 12345L
    private val nomisId = 54321L

    @Nested
    @DisplayName("when NOMIS is the origin of a contact update")
    inner class WhenNomisUpdated {
      @BeforeEach
      fun setUp() {
        publishContactDomainEvent("core-person-record.prison.contact.updated", prisonNumber, cprContactId, source = "nomis")
        waitForAnyProcessingToComplete("core-person-contact-update-ignored")
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("core-person-contact-update-ignored"),
          check {
            assertThat(it).containsEntry("prisonNumber", prisonNumber)
            assertThat(it).containsEntry("cprContactId", cprContactId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when CPR is the origin of a contact update")
    inner class WhenCprUpdated {
      @Nested
      @DisplayName("when contact is a phone number")
      inner class HappyPathPhone {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(
            cprContactId,
            contactMapping(cprContactId).copy(nomisId = nomisId, nomisContactType = CorePersonContactMappingDto.NomisContactType.PHONE),
          )
          corePersonCprApi.stubGetPrisonerContact(
            prisonNumber,
            cprContactId,
            prisonerContact(prisonNumber).copy(type = PrisonContact.Type.MOBILE, value = "07700 900000", extension = "123"),
          )
          nomisApi.stubGetPrisonerDetails(prisonNumber, rootOffenderId = rootOffenderId)
          nomisApi.stubUpdateOffenderPhone(rootOffenderId, phoneId = nomisId)

          publishContactDomainEvent("core-person-record.prison.contact.updated", prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-update-success")
        }

        @Test
        fun `will call CPR to get the contact details`() {
          corePersonCprApi.verify(
            getRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/contact/$cprContactId")),
          )
        }

        @Test
        fun `will update the phone in NOMIS against the root offender`() {
          nomisApi.verify(
            putRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/phone/$nomisId"))
              .withRequestBodyJsonPath("number", "07700 900000")
              .withRequestBodyJsonPath("extension", "123")
              .withRequestBodyJsonPath("typeCode", "MOB"),
          )
        }

        @Test
        fun `will send success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-contact-update-success"),
            check {
              assertThat(it).containsEntry("prisonNumber", prisonNumber)
              assertThat(it).containsEntry("cprContactId", cprContactId)
              assertThat(it).containsEntry("rootOffenderId", rootOffenderId.toString())
              assertThat(it).containsEntry("nomisContactType", "PHONE")
              assertThat(it).containsEntry("nomisId", nomisId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when updating a phone number associated with an address")
      inner class HappyPathAddressPhone {
        private val cprAddressId = "22222222-3333-4444-5555-666666666666"
        private val nomisAddressId = 67890L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(
            cprContactId,
            contactMapping(cprContactId).copy(nomisId = nomisId, nomisContactType = CorePersonContactMappingDto.NomisContactType.PHONE),
          )
          mappingApi.stubGetAddressMapping(
            cprAddressId,
            mapping = CorePersonAddressMappingDto(
              cprId = cprAddressId,
              nomisId = nomisAddressId,
              nomisPrisonNumber = prisonNumber,
              mappingType = CorePersonAddressMappingDto.MappingType.CPR_CREATED,
            ),
          )
          corePersonCprApi.stubGetPrisonerContact(
            prisonNumber,
            cprContactId,
            prisonerContact(prisonNumber).copy(
              type = PrisonContact.Type.MOBILE,
              value = "07700 900000",
              extension = "123",
              cprAddressId = cprAddressId,
            ),
          )
          nomisApi.stubGetPrisonerDetails(prisonNumber, rootOffenderId = rootOffenderId)
          nomisApi.stubUpdateOffenderAddressPhone(rootOffenderId, nomisAddressId, phoneId = nomisId)

          publishContactDomainEvent("core-person-record.prison.contact.updated", prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-update-success")
        }

        @Test
        fun `will look up the NOMIS address mapping for the CPR address`() {
          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/core-person/address/cpr-address-id/$cprAddressId")),
          )
        }

        @Test
        fun `will update the phone in NOMIS against the mapped address`() {
          nomisApi.verify(
            putRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/address/$nomisAddressId/phone/$nomisId"))
              .withRequestBodyJsonPath("number", "07700 900000")
              .withRequestBodyJsonPath("extension", "123")
              .withRequestBodyJsonPath("typeCode", "MOB"),
          )
        }
      }

      @Nested
      @DisplayName("when contact is an email address")
      inner class HappyPathEmail {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(
            cprContactId,
            contactMapping(cprContactId).copy(nomisId = nomisId, nomisContactType = CorePersonContactMappingDto.NomisContactType.EMAIL),
          )
          corePersonCprApi.stubGetPrisonerContact(
            prisonNumber,
            cprContactId,
            prisonerContact(prisonNumber).copy(type = PrisonContact.Type.EMAIL, value = "test@justice.gov.uk"),
          )
          nomisApi.stubGetPrisonerDetails(prisonNumber, rootOffenderId = rootOffenderId)
          nomisApi.stubUpdateOffenderEmail(rootOffenderId, emailAddressId = nomisId)

          publishContactDomainEvent("core-person-record.prison.contact.updated", prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-update-success")
        }

        @Test
        fun `will update the email in NOMIS against the root offender`() {
          nomisApi.verify(
            putRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/email/$nomisId"))
              .withRequestBodyJsonPath("email", "test@justice.gov.uk"),
          )
        }
      }

      @Nested
      @DisplayName("when contact type has changed between phone and email")
      inner class TypeChanged {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetContactMapping(
            cprContactId,
            contactMapping(cprContactId).copy(nomisId = nomisId, nomisContactType = CorePersonContactMappingDto.NomisContactType.PHONE),
          )
          corePersonCprApi.stubGetPrisonerContact(
            prisonNumber,
            cprContactId,
            prisonerContact(prisonNumber).copy(type = PrisonContact.Type.EMAIL, value = "test@justice.gov.uk"),
          )

          publishContactDomainEvent("core-person-record.prison.contact.updated", prisonNumber, cprContactId)
          waitForAnyProcessingToComplete("core-person-contact-update-failed")
        }

        @Test
        fun `will not update NOMIS`() {
          nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/phone/$nomisId")))
          nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/core-person/$rootOffenderId/email/$nomisId")))
        }

        @Test
        fun `will send failure telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-contact-update-failed"),
            check {
              assertThat(it).containsEntry("cprContactId", cprContactId)
              assertThat(it["reason"]).contains("has changed from PHONE to EMAIL")
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun publishContactCreatedDomainEvent(prisonNumber: String, cprContactId: String, source: String = "core-person-record") = publishContactDomainEvent("core-person-record.prison.contact.created", prisonNumber, cprContactId, source)

  private fun publishContactDomainEvent(eventType: String, prisonNumber: String, cprContactId: String, source: String = "core-person-record") {
    val payload = """
      {
        "eventType": "$eventType",
        "additionalInformation": { "cprContactId": "$cprContactId" },
        "personReference": {
          "identifiers": [{ "type": "prisonNumber", "value": "$prisonNumber" }]
        }
      }
    """.trimIndent()

    awsSnsClient.publish(
      PublishRequest.builder()
        .topicArn(topicArn)
        .message(payload)
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
            "eventSource" to MessageAttributeValue.builder().dataType("String").stringValue(source).build(),
          ),
        )
        .build(),
    ).get()
  }
}
