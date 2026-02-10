package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddressDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddressPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmailDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactReconcileDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.linkedPrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

class ContactPersonRepairResourceIntTest(
  @Autowired
  private val nomisApi: ContactPersonNomisApiMockServer,
  @Autowired
  private val mappingApi: ContactPersonMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = ContactPersonDpsApiExtension.dpsContactPersonServer
  private val nomisPersonIdAndDpsContactId = 43321L

  @DisplayName("POST /contacts/{contactId}/resynchronise")
  @Nested
  inner class RepairContact {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/contacts/{contactId}/resynchronise", nomisPersonIdAndDpsContactId)
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/contacts/{contactId}/resynchronise", nomisPersonIdAndDpsContactId)
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/contacts/{contactId}/resynchronise", nomisPersonIdAndDpsContactId)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `will DPS contact does not exist`() {
        webTestClient.post().uri("/contacts/{contactId}/resynchronise", 999)
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class HappyPath {
      private val dpsContactEmailId = 8274972472L
      private val nomisEmailId = 1763713L
      private val dpsContactAddressId = 2412741L
      private val nomisAddressId = 92649264L
      private val dpsContactPhoneId = 273724L
      private val nomisPhoneId = 983264L
      private val dpsContactAddressPhoneId = 958484L
      private val nomisAddressPhoneId = 18364L
      private val dpsPrisonerContactId = 98463L
      private val nomisContactId = 18437L

      @BeforeEach
      fun setUp() {
        reset(telemetryClient)
        dpsApi.stubGetContactDetails(
          nomisPersonIdAndDpsContactId,
          contactReconcileDetails(contactId = nomisPersonIdAndDpsContactId).copy(
            firstName = "KWEKU",
            lastName = "KOFI",
            emails = listOf(contactEmailDetails().copy(contactEmailId = dpsContactEmailId)),
            addresses = listOf(
              contactAddressDetails().copy(
                contactAddressId = dpsContactAddressId,
                addressPhones = listOf(contactAddressPhoneDetails().copy(contactAddressPhoneId = dpsContactAddressPhoneId)),
              ),
            ),
            phones = listOf(contactPhoneDetails().copy(contactPhoneId = dpsContactPhoneId)),
            relationships = listOf(linkedPrisonerDetails().copy(prisonerContactId = dpsPrisonerContactId)),
          ),
        )
        stubCreateContactPerson(nomisPersonIdAndDpsContactId)
        stubCreateContactEmail(
          nomisPersonIdAndDpsContactId = nomisPersonIdAndDpsContactId,
          dpsContactEmailId = dpsContactEmailId,
          nomisEmailId = nomisEmailId,
        )
        stubCreateContactAddress(
          nomisPersonIdAndDpsContactId = nomisPersonIdAndDpsContactId,
          dpsContactAddressId = dpsContactAddressId,
          nomisAddressId = nomisAddressId,
        )

        stubCreateContactPhone(
          nomisPersonIdAndDpsContactId = nomisPersonIdAndDpsContactId,
          dpsContactPhoneId = dpsContactPhoneId,
          nomisPhoneId = nomisPhoneId,
        )

        stubCreateContactAddressPhone(
          nomisPersonIdAndDpsContactId = nomisPersonIdAndDpsContactId,
          dpsContactAddressId = dpsContactAddressId,
          nomisAddressId = nomisAddressId,
          dpsContactAddressPhoneId = dpsContactAddressPhoneId,
          nomisAddressPhoneId = nomisAddressPhoneId,
        )

        stubCreatePrisonerContact(
          nomisPersonIdAndDpsContactId = nomisPersonIdAndDpsContactId,
          dpsPrisonerContactId = dpsPrisonerContactId,
          nomisContactId = nomisContactId,
        )

        webTestClient.post().uri("/contacts/{contactId}/resynchronise", nomisPersonIdAndDpsContactId)
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
          .exchange()
          .expectStatus().isOk
      }

      fun stubCreateContactPerson(nomisPersonIdAndDpsContactId: Long) {
        mappingApi.stubGetByDpsContactIdOrNull(dpsContactId = nomisPersonIdAndDpsContactId, null)
        dpsApi.stubGetContact(
          contactId = nomisPersonIdAndDpsContactId,
          contact().copy(
            id = nomisPersonIdAndDpsContactId,
            firstName = "KWEKU",
            lastName = "KOFI",
            middleName = "Steve",
          ),
        )
        nomisApi.stubCreatePerson(createPersonResponse().copy(personId = nomisPersonIdAndDpsContactId))
        mappingApi.stubCreatePersonMapping()
      }

      fun stubCreateContactEmail(nomisPersonIdAndDpsContactId: Long, dpsContactEmailId: Long, nomisEmailId: Long) {
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
      }
      fun stubCreateContactAddress(nomisPersonIdAndDpsContactId: Long, dpsContactAddressId: Long, nomisAddressId: Long) {
        mappingApi.stubGetByDpsContactAddressIdOrNullAsNullFollowedByValue(dpsContactAddressId = dpsContactAddressId, PersonAddressMappingDto(dpsId = dpsContactAddressId.toString(), nomisId = nomisAddressId, PersonAddressMappingDto.MappingType.DPS_CREATED))
        dpsApi.stubGetContactAddress(
          contactAddressId = dpsContactAddressId,
          contactAddress().copy(
            contactAddressId = dpsContactAddressId,
            contactId = nomisPersonIdAndDpsContactId,
          ),
        )
        nomisApi.stubCreatePersonAddress(personId = nomisPersonIdAndDpsContactId, createPersonAddressResponse().copy(personAddressId = nomisAddressId))
        mappingApi.stubCreateAddressMapping()
      }
      fun stubCreateContactPhone(nomisPersonIdAndDpsContactId: Long, dpsContactPhoneId: Long, nomisPhoneId: Long) {
        mappingApi.stubGetByDpsContactPhoneIdOrNull(dpsContactPhoneId = dpsContactPhoneId, null)
        dpsApi.stubGetContactPhone(
          contactPhoneId = dpsContactPhoneId,
          contactPhone().copy(
            contactPhoneId = dpsContactPhoneId,
            contactId = nomisPersonIdAndDpsContactId,
          ),
        )
        nomisApi.stubCreatePersonPhone(personId = nomisPersonIdAndDpsContactId, createPersonPhoneResponse().copy(phoneId = nomisPhoneId))
        mappingApi.stubCreatePhoneMapping()
      }

      fun stubCreateContactAddressPhone(nomisPersonIdAndDpsContactId: Long, dpsContactAddressId: Long, nomisAddressId: Long, dpsContactAddressPhoneId: Long, nomisAddressPhoneId: Long) {
        mappingApi.stubGetByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId = dpsContactAddressPhoneId, null)
        dpsApi.stubGetContactAddressPhone(
          contactAddressPhoneId = dpsContactAddressPhoneId,
          contactAddressPhone().copy(
            contactPhoneId = dpsContactAddressPhoneId,
            contactId = nomisPersonIdAndDpsContactId,
            contactAddressId = dpsContactAddressId,
          ),
        )
        nomisApi.stubCreatePersonAddressPhone(personId = nomisPersonIdAndDpsContactId, addressId = nomisAddressId, createPersonPhoneResponse().copy(phoneId = nomisAddressPhoneId))
        mappingApi.stubCreatePhoneMapping()
      }

      fun stubCreatePrisonerContact(nomisPersonIdAndDpsContactId: Long, dpsPrisonerContactId: Long, nomisContactId: Long) {
        mappingApi.stubGetByDpsPrisonerContactIdOrNull(dpsPrisonerContactId = dpsPrisonerContactId, null)
        dpsApi.stubGetPrisonerContact(
          prisonerContactId = dpsPrisonerContactId,
          prisonerContact().copy(
            id = dpsPrisonerContactId,
            contactId = nomisPersonIdAndDpsContactId,
            prisonerNumber = "A1234KT",
          ),
        )
        nomisApi.stubCreatePersonContact(personId = nomisPersonIdAndDpsContactId, createPersonContactResponse().copy(personContactId = nomisContactId))
        mappingApi.stubCreateContactMapping()
      }

      @Test
      fun `will create person in NOMIS`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/persons")))
      }

      @Test
      fun `will create the email in NOMIS`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/email")))
      }

      @Test
      fun `will create the address in NOMIS`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address")))
      }

      @Test
      fun `will create the phone in NOMIS`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/phone")))
      }

      @Test
      fun `will create the address phone in NOMIS`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/address/$nomisAddressId/phone")))
      }

      @Test
      fun `will create the contact in NOMIS`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/persons/$nomisPersonIdAndDpsContactId/contact")))
      }

      @Test
      fun `will output repair telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("to-nomis-synch-contactperson-resynchronisation-repair"),
          check { assertThat(it["dpsContactId"]).isEqualTo(nomisPersonIdAndDpsContactId.toString()) },
          isNull(),
        )
      }
    }
  }

  @DisplayName("PUT /contacts/{contactId}/prisoner-contact/{prisonerContactId}/resynchronise")
  @Nested
  inner class RepairPrisonerContact {
    val dpsPrisonerContactId = 786756L
    private val nomisContactId = 974592L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/contacts/{contactId}/prisoner-contact/{prisonerContactId}/resynchronise", nomisPersonIdAndDpsContactId, dpsPrisonerContactId)
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/contacts/{contactId}/prisoner-contact/{prisonerContactId}/resynchronise", nomisPersonIdAndDpsContactId, dpsPrisonerContactId)
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/contacts/{contactId}/prisoner-contact/{prisonerContactId}/resynchronise", nomisPersonIdAndDpsContactId, dpsPrisonerContactId)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        reset(telemetryClient)
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

        webTestClient.put().uri("/contacts/{contactId}/prisoner-contact/{prisonerContactId}/resynchronise", nomisPersonIdAndDpsContactId, dpsPrisonerContactId)
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `telemetry will contain key facts about the contact updated`() {
        verify(telemetryClient).trackEvent(
          ArgumentMatchers.eq("contact-update-success"),
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

      @Test
      fun `will output repair telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("to-nomis-synch-contactperson-prisoner-contact-resynchronisation-repair"),
          check {
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonIdAndDpsContactId.toString())
            assertThat(it["dpsPrisonerContactId"]).isEqualTo(dpsPrisonerContactId.toString())
          },
          isNull(),
        )
      }
    }
  }
}
