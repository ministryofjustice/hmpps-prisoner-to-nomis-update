@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactForPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddressDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactAddressPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmailDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmploymentDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactIdentityDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactRestrictionDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.linkedPrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.linkedPrisonerRelationshipDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestrictionDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestrictionsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactSummaryPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.contactPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.contactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.personAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.personContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.personEmailAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.personEmployment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.personIdentifier
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.personPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.prisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ContactDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ContactRestrictionDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.LinkedPrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@SpringAPIServiceTest
@Import(
  ContactPersonReconciliationService::class,
  ContactPersonNomisApiService::class,
  NomisApiService::class,
  ContactPersonDpsApiService::class,
  ContactPersonConfiguration::class,
  RetryApiService::class,
  ContactPersonNomisApiMockServer::class,
)
internal class ContactPersonReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var nomisApi: ContactPersonNomisApiMockServer

  private val dpsApi = ContactPersonDpsApiExtension.Companion.dpsContactPersonServer

  @Autowired
  private lateinit var service: ContactPersonReconciliationService

  @Nested
  inner class CheckPrisonerContactsMatch {
    val prisonerId: PrisonerIds = PrisonerIds(
      bookingId = 1,
      offenderNo = "A1234KT",
    )
    private fun stubContact(dpsContact: PrisonerContactSummary, nomisContact: PrisonerContact) {
      nomisApi.stubGetPrisonerContacts(
        prisonerId.offenderNo,
        prisonerWithContacts().copy(
          contacts = listOf(nomisContact),
        ),
      )
      dpsApi.stubGetPrisonerContacts(
        prisonerId.offenderNo,
        prisonerContactSummaryPage().copy(
          totalElements = 1,
          content = listOf(dpsContact),
        ),
      )
    }

    @Nested
    inner class WhenPrisonerHasNoContacts {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = emptyList()))
        dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = emptyList()))
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkPrisonerContactsMatch(prisonerId),
        ).isNull()
      }
    }

    @Nested
    inner class WhenPrisonerHasSameContacts {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(1), prisonerContact(2))))
        dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 2, content = listOf(prisonerContactSummary(1, prisonerContactId = 11), prisonerContactSummary(2, prisonerContactId = 22))))
        dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 11)
        dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 22)
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkPrisonerContactsMatch(prisonerId),
        ).isNull()
      }
    }

    @Nested
    inner class WhenPrisonerHasSameDuplicateContactsButInDifferentOrder {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetPrisonerContacts(
          prisonerId.offenderNo,
          prisonerWithContacts().copy(
            contacts = listOf(
              prisonerContact(personId = 1, contactId = 1).copy(approvedVisitor = true),
              prisonerContact(personId = 1, contactId = 2).copy(approvedVisitor = false),
            ),
          ),
        )
        dpsApi.stubGetPrisonerContacts(
          prisonerId.offenderNo,
          prisonerContactSummaryPage().copy(
            totalElements = 2,
            content = listOf(
              prisonerContactSummary(contactId = 1, prisonerContactId = 11).copy(isApprovedVisitor = false),
              prisonerContactSummary(contactId = 1, prisonerContactId = 22).copy(isApprovedVisitor = true),
            ),
          ),
        )
        dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 11)
        dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 22)
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkPrisonerContactsMatch(prisonerId),
        ).isNull()
      }
    }

    @Nested
    inner class WhenContactIsMissingInDPS {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(1))))
        dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = emptyList()))
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPrisonerContactsMatch(prisonerId)).isNotNull.extracting { it!!.offenderNo }.isEqualTo(prisonerId.offenderNo)
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to prisonerId.offenderNo,
              "dpsContactCount" to "0",
              "nomisContactCount" to "1",
              "contactIdsMissingFromNomis" to "[]",
              "contactIdsMissingFromDps" to "[1]",
              "reason" to "different-number-of-contacts",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenContactIsMissingInNOMIS {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf()))
        dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = listOf(prisonerContactSummary(1))))
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPrisonerContactsMatch(prisonerId)).isNotNull.extracting { it!!.offenderNo }.isEqualTo(prisonerId.offenderNo)
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to prisonerId.offenderNo,
              "dpsContactCount" to "1",
              "nomisContactCount" to "0",
              "contactIdsMissingFromNomis" to "[1]",
              "contactIdsMissingFromDps" to "[]",
              "reason" to "different-number-of-contacts",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenBothHaveASingleContactButDifferentContactPersonId {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(88))))
        dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = listOf(prisonerContactSummary(99))))
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPrisonerContactsMatch(prisonerId)).isNotNull.extracting { it!!.offenderNo }.isEqualTo(prisonerId.offenderNo)
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to prisonerId.offenderNo,
              "dpsContactCount" to "1",
              "nomisContactCount" to "1",
              "contactIdsMissingFromNomis" to "[99]",
              "contactIdsMissingFromDps" to "[88]",
              "reason" to "different-contacts",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenContactNameDoesNotMatch {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactSummary().copy(
            contactId = 99,
            firstName = "SARAH",
            lastName = "SMITH",
          ),
          nomisContact = prisonerContact().copy(
            person = ContactForPerson(
              personId = 99,
              firstName = "JANE",
              lastName = "SMITH",
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPrisonerContactsMatch(prisonerId)).isNotNull.extracting { it!!.offenderNo }.isEqualTo(prisonerId.offenderNo)
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          eq(
            mapOf(
              "offenderNo" to prisonerId.offenderNo,
              "dpsContactCount" to "1",
              "nomisContactCount" to "1",
              "contactIdsMissingFromNomis" to "[]",
              "contactIdsMissingFromDps" to "[]",
              "contactId" to "99",
              "reason" to "different-contacts-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenContactApprovedStatusDoNotMatch {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactSummary().copy(
            isApprovedVisitor = true,
          ),
          nomisContact = prisonerContact().copy(
            approvedVisitor = false,
          ),
        )
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("reason", "different-contacts-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenContactTypeDoNotMatch {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactSummary().copy(
            relationshipTypeCode = "S",
            relationshipTypeDescription = "Social",
          ),
          nomisContact = prisonerContact().copy(
            contactType = CodeDescription("O", "Official"),
          ),
        )
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("reason", "different-contacts-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenRelationshipTypeDoNotMatch {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactSummary().copy(
            relationshipToPrisonerCode = "BRO",
            relationshipTypeDescription = "Brother",
          ),
          nomisContact = prisonerContact().copy(
            relationshipType = CodeDescription("FRI", "Friend"),
          ),
        )
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("reason", "different-contacts-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenOneIsActiveButNotTheOther {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactSummary().copy(
            isRelationshipActive = true,
          ),
          nomisContact = prisonerContact().copy(
            active = false,
          ),
        )
      }
    }

    @Nested
    inner class WhenOneIsEmergencyContactButNotTheOther {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactSummary().copy(
            isEmergencyContact = true,
          ),
          nomisContact = prisonerContact().copy(
            emergencyContact = false,
          ),
        )
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("reason", "different-contacts-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenOneIsNextOfKinButNotTheOther {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactSummary().copy(
            isNextOfKin = true,
          ),
          nomisContact = prisonerContact().copy(
            nextOfKin = false,
          ),
        )
      }

      @Test
      fun `telemetry will show contact is missing`() = runTest {
        service.checkPrisonerContactsMatch(prisonerId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-prisoner-contact-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("reason", "different-contacts-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class RestrictionsComparison {
      @Nested
      inner class WhenPrisonerHasNoRestrictions {
        @BeforeEach
        fun beforeEach() {
          nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(1).copy(restrictions = emptyList()))))
          dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = listOf(prisonerContactSummary(1, prisonerContactId = 11))))
          dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 11, response = prisonerContactRestrictionsResponse().copy(prisonerContactRestrictions = emptyList()))
        }

        @Test
        fun `will not report a mismatch`() = runTest {
          assertThat(
            service.checkPrisonerContactsMatch(prisonerId),
          ).isNull()
        }
      }

      @Nested
      inner class WhenPrisonerHasSameRestrictions {
        @BeforeEach
        fun beforeEach() {
          nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(1).copy(restrictions = listOf(contactRestriction())))))
          dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = listOf(prisonerContactSummary(1, prisonerContactId = 11))))
          dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 11, response = prisonerContactRestrictionsResponse().copy(prisonerContactRestrictions = listOf(prisonerContactRestrictionDetails(1))))
        }

        @Test
        fun `will not report a mismatch`() = runTest {
          assertThat(
            service.checkPrisonerContactsMatch(prisonerId),
          ).isNull()
        }
      }

      @Nested
      inner class WhenRestrictionIsMissingInDPS {
        @BeforeEach
        fun beforeEach() {
          nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(1).copy(restrictions = listOf(contactRestriction())))))
          dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = listOf(prisonerContactSummary(1, prisonerContactId = 11))))
          dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 11, response = prisonerContactRestrictionsResponse().copy(prisonerContactRestrictions = listOf()))
        }

        @Test
        fun `will report a mismatch`() = runTest {
          assertThat(service.checkPrisonerContactsMatch(prisonerId)).isNotNull.extracting { it!!.offenderNo }.isEqualTo(prisonerId.offenderNo)
        }

        @Test
        fun `telemetry will show restriction is missing`() = runTest {
          service.checkPrisonerContactsMatch(prisonerId)
          verify(telemetryClient).trackEvent(
            eq("contact-person-prisoner-contact-reconciliation-mismatch"),
            eq(
              mapOf(
                "offenderNo" to prisonerId.offenderNo,
                "contactId" to "1",
                "relationshipType" to "BRO",
                "dpsPrisonerContactRestrictionCount" to "0",
                "nomisPrisonerContactRestrictionCount" to "1",
                "prisonerRestrictionsTypesMissingFromNomis" to "[]",
                "prisonerRestrictionsTypesMissingFromDps" to "[BAN]",
                "reason" to "different-number-of-contact-restrictions",
              ),
            ),
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenRestrictionIsMissingInNOMIS {
        @BeforeEach
        fun beforeEach() {
          nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(1).copy(restrictions = listOf()))))
          dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = listOf(prisonerContactSummary(1, prisonerContactId = 11))))
          dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 11, response = prisonerContactRestrictionsResponse().copy(prisonerContactRestrictions = listOf(prisonerContactRestrictionDetails(1))))
        }

        @Test
        fun `will report a mismatch`() = runTest {
          assertThat(service.checkPrisonerContactsMatch(prisonerId)).isNotNull.extracting { it!!.offenderNo }.isEqualTo(prisonerId.offenderNo)
        }

        @Test
        fun `telemetry will show restriction is missing`() = runTest {
          service.checkPrisonerContactsMatch(prisonerId)
          verify(telemetryClient).trackEvent(
            eq("contact-person-prisoner-contact-reconciliation-mismatch"),
            eq(
              mapOf(
                "offenderNo" to prisonerId.offenderNo,
                "contactId" to "1",
                "relationshipType" to "BRO",
                "dpsPrisonerContactRestrictionCount" to "1",
                "nomisPrisonerContactRestrictionCount" to "0",
                "prisonerRestrictionsTypesMissingFromNomis" to "[BAN]",
                "prisonerRestrictionsTypesMissingFromDps" to "[]",
                "reason" to "different-number-of-contact-restrictions",
              ),
            ),
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenBothHaveASingleRestrictionButDifferentRestrictionType {
        @BeforeEach
        fun beforeEach() {
          nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(1).copy(restrictions = listOf(contactRestriction().copy(type = CodeDescription("CCTV", "CCTV")))))))
          dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = listOf(prisonerContactSummary(1, prisonerContactId = 11))))
          dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 11, response = prisonerContactRestrictionsResponse().copy(prisonerContactRestrictions = listOf(prisonerContactRestrictionDetails(1).copy(restrictionType = "BAN"))))
        }

        @Test
        fun `will report a mismatch`() = runTest {
          assertThat(service.checkPrisonerContactsMatch(prisonerId)).isNotNull.extracting { it!!.offenderNo }.isEqualTo(prisonerId.offenderNo)
        }

        @Test
        fun `telemetry will show restriction is missing`() = runTest {
          service.checkPrisonerContactsMatch(prisonerId)
          verify(telemetryClient).trackEvent(
            eq("contact-person-prisoner-contact-reconciliation-mismatch"),
            eq(
              mapOf(
                "offenderNo" to prisonerId.offenderNo,
                "contactId" to "1",
                "relationshipType" to "BRO",
                "dpsPrisonerContactRestrictionCount" to "1",
                "nomisPrisonerContactRestrictionCount" to "1",
                "prisonerRestrictionsTypesMissingFromNomis" to "[BAN]",
                "prisonerRestrictionsTypesMissingFromDps" to "[CCTV]",
                "reason" to "different-prisoner-contact-restrictions-types",
              ),
            ),
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenHasSameRestrictionButDifferentDates {
        @BeforeEach
        fun beforeEach() {
          nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = listOf(prisonerContact(1).copy(restrictions = listOf(contactRestriction().copy(expiryDate = LocalDate.parse("2023-01-01")))))))
          dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 1, content = listOf(prisonerContactSummary(1, prisonerContactId = 11))))
          dpsApi.stubGetPrisonerContactRestrictions(prisonerContactId = 11, response = prisonerContactRestrictionsResponse().copy(prisonerContactRestrictions = listOf(prisonerContactRestrictionDetails(1).copy(expiryDate = LocalDate.parse("2022-01-01")))))
        }

        @Test
        fun `will report a mismatch`() = runTest {
          assertThat(service.checkPrisonerContactsMatch(prisonerId)).isNotNull.extracting { it!!.offenderNo }.isEqualTo(prisonerId.offenderNo)
        }

        @Test
        fun `telemetry will show restriction is missing`() = runTest {
          service.checkPrisonerContactsMatch(prisonerId)
          verify(telemetryClient).trackEvent(
            eq("contact-person-prisoner-contact-reconciliation-mismatch"),
            eq(
              mapOf(
                "offenderNo" to prisonerId.offenderNo,
                "contactId" to "1",
                "relationshipType" to "BRO",
                "dpsPrisonerContactRestrictionCount" to "1",
                "nomisPrisonerContactRestrictionCount" to "1",
                "restrictionType" to "BAN",
                "reason" to "different-prisoner-contact-restrictions-details",
              ),
            ),
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  inner class CheckPersonContactsMatch {
    val personId = 1L
    val nomisPerson = contactPerson(personId = personId).copy(
      firstName = "KWEKU",
      lastName = "KOFI",
      phoneNumbers = emptyList(),
      employments = emptyList(),
      identifiers = emptyList(),
      addresses = emptyList(),
      emailAddresses = emptyList(),
      contacts = emptyList(),
      restrictions = emptyList(),
    )
    val dpsContact = contactDetails(contactId = personId).copy(
      firstName = "KWEKU",
      lastName = "KOFI",
    )

    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
    }

    private fun stubPersonContact(nomisPerson: ContactPerson, dpsContact: ContactDetails, dpsContactRestrictions: List<ContactRestrictionDetails> = emptyList(), dpsPrisonerContacts: List<LinkedPrisonerDetails> = emptyList()) {
      nomisApi.stubGetPerson(personId, nomisPerson)
      dpsApi.stubGetContactDetails(personId, dpsContact)
      dpsApi.stubGetContactRestrictions(personId, dpsContactRestrictions)
      dpsApi.stubGetLinkedPrisonerContacts(personId, dpsPrisonerContacts)
    }

    @Nested
    inner class WhenPersonHasMinimalDataThatMatches {

      @BeforeEach
      fun setUp() {
        stubPersonContact(nomisPerson, dpsContact)
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkPersonContactMatch(personId),
        ).isNull()
      }
    }

    @Nested
    inner class WhenPersonHasLotsOfDataThatMatches {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            firstName = "KWEKU",
            lastName = "KOFI",
            dateOfBirth = LocalDate.parse("1980-01-01"),
            phoneNumbers = listOf(personPhoneNumber("0114555555")),
            addresses = listOf(personAddress(phoneNumbers = listOf(personPhoneNumber("01146666666")))),
            emailAddresses = listOf(personEmailAddress("test@justice.gov.uk")),
            employments = listOf(personEmployment(corporateId = 98765)),
            identifiers = listOf(personIdentifier("SMITH1717171")),
            contacts = listOf(
              personContact("A1234KT").copy(relationshipType = CodeDescription("BRO", "Brother"), active = false),
              personContact("A1234KT").copy(relationshipType = CodeDescription("FRI", "Friend"), active = true),
              personContact("A1000KT").copy(relationshipType = CodeDescription("GIR", "Girlfriend"), active = true),
              // old booking so will be ignored
              personContact("A9999KT").copy(relationshipType = CodeDescription("GIR", "Girlfriend"), prisoner = personContact("A9999KT").prisoner.copy(bookingSequence = 2)),
            ),
            restrictions = listOf(
              contactRestriction().copy(
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2023-01-01"),
                expiryDate = LocalDate.parse("2024-01-30"),
              ),
              contactRestriction().copy(
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2024-01-31"),
                expiryDate = null,
              ),
              contactRestriction().copy(
                type = CodeDescription("CCTV", "CCTV"),
                effectiveDate = LocalDate.parse("2024-01-31"),
                expiryDate = null,
              ),
            ),
          ),
          dpsContact.copy(
            firstName = "KWEKU",
            lastName = "KOFI",
            dateOfBirth = LocalDate.parse("1980-01-01"),
            phoneNumbers = listOf(contactPhoneDetails("0114555555")),
            addresses = listOf(contactAddressDetails(phoneNumbers = listOf(contactAddressPhoneDetails("01146666666")))),
            emailAddresses = listOf(contactEmailDetails("test@justice.gov.uk")),
            employments = listOf(contactEmploymentDetails(98765)),
            identities = listOf(contactIdentityDetails("SMITH1717171")),
          ),
          dpsContactRestrictions = listOf(
            contactRestrictionDetails().copy(
              restrictionType = "BAN",
              startDate = LocalDate.parse("2023-01-01"),
              expiryDate = LocalDate.parse("2024-01-30"),
            ),
            contactRestrictionDetails().copy(
              restrictionType = "BAN",
              startDate = LocalDate.parse("2024-01-31"),
              expiryDate = null,
            ),
            contactRestrictionDetails().copy(
              restrictionType = "CCTV",
              startDate = LocalDate.parse("2024-01-31"),
              expiryDate = null,
            ),
          ),
          dpsPrisonerContacts = listOf(
            linkedPrisonerDetails().copy(
              prisonerNumber = "A1234KT",
              relationships = listOf(
                linkedPrisonerRelationshipDetails().copy(
                  relationshipToPrisonerCode = "BRO",
                  isRelationshipActive = false,
                ),
                linkedPrisonerRelationshipDetails().copy(
                  relationshipToPrisonerCode = "FRI",
                  isRelationshipActive = true,
                ),
              ),
            ),
            linkedPrisonerDetails().copy(
              prisonerNumber = "A1000KT",
              relationships = listOf(
                linkedPrisonerRelationshipDetails().copy(
                  relationshipToPrisonerCode = "GIR",
                  isRelationshipActive = true,
                ),
              ),
            ),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkPersonContactMatch(personId),
        ).isNull()
      }
    }

    @Nested
    inner class WhenNamesDoNotMatch {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            firstName = "KWEKU",
            lastName = "KOFI",
          ),
          dpsContact.copy(
            firstName = "ADAM",
            lastName = "SMITH",
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDateOfBirthDoNotMatch {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            dateOfBirth = LocalDate.parse("1980-01-01"),
          ),
          dpsContact.copy(
            dateOfBirth = LocalDate.parse("1990-02-01"),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenADifferentNumberOfAddress {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            addresses = listOf(personAddress(phoneNumbers = emptyList()), personAddress(phoneNumbers = emptyList())),
          ),
          dpsContact.copy(
            addresses = listOf(contactAddressDetails(phoneNumbers = emptyList())),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentAddressPhoneNumbers {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            addresses = listOf(personAddress(phoneNumbers = listOf(personPhoneNumber("01145555555")))),
          ),
          dpsContact.copy(
            addresses = listOf(contactAddressDetails(phoneNumbers = listOf(contactAddressPhoneDetails("01146666666")))),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentPersonPhoneNumbers {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            phoneNumbers = listOf(personPhoneNumber("01145555555")),
          ),
          dpsContact.copy(
            phoneNumbers = listOf(contactPhoneDetails("01146666666")),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentEmailAddress {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            emailAddresses = listOf(personEmailAddress("john@gmail.com")),
          ),
          dpsContact.copy(
            emailAddresses = listOf(contactEmailDetails("bob@justice.gov.uk")),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentIdentifiers {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            identifiers = listOf(personIdentifier("DVLASMAITH28282")),
          ),
          dpsContact.copy(
            identities = listOf(contactIdentityDetails("PASS838383")),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenWorksADifferentEmployers {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            employments = listOf(personEmployment(corporateId = 1234)),
          ),
          dpsContact.copy(
            employments = listOf(contactEmploymentDetails(organisationId = 8765)),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentActiveContacts {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            contacts = listOf(
              personContact("A1234KT").copy(
                relationshipType = CodeDescription("BRO", "Brother"),
                active = false,
              ),
            ),
          ),
          dpsContact,
          dpsPrisonerContacts = listOf(
            linkedPrisonerDetails().copy(
              prisonerNumber = "A1234KT",
              relationships = listOf(
                linkedPrisonerRelationshipDetails().copy(
                  relationshipToPrisonerCode = "BRO",
                  isRelationshipActive = true,
                ),
              ),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentPrisonerRelationships {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            contacts = listOf(
              personContact("A1234KT").copy(
                relationshipType = CodeDescription("GIR", "Girlfriend"),
              ),
            ),
          ),
          dpsContact,
          dpsPrisonerContacts = listOf(
            linkedPrisonerDetails().copy(
              prisonerNumber = "A1234KT",
              relationships = listOf(
                linkedPrisonerRelationshipDetails().copy(
                  relationshipToPrisonerCode = "BRO",
                ),
              ),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentNumberOfPrisonerRelationships {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            contacts = listOf(
              personContact("A1234KT").copy(
                relationshipType = CodeDescription("BRO", "Brother"),
              ),
              personContact("A1000KT").copy(
                relationshipType = CodeDescription("GIR", "Girlfriend"),
              ),
            ),
          ),
          dpsContact,
          dpsPrisonerContacts = listOf(
            linkedPrisonerDetails().copy(
              prisonerNumber = "A1234KT",
              relationships = listOf(
                linkedPrisonerRelationshipDetails().copy(
                  relationshipToPrisonerCode = "BRO",
                ),
              ),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentActiveRestrictions {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            restrictions = listOf(
              contactRestriction().copy(
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2023-01-01"),
                expiryDate = LocalDate.parse("2024-01-30"),
              ),
            ),
          ),
          dpsContact,
          dpsContactRestrictions = listOf(
            contactRestrictionDetails().copy(
              restrictionType = "BAN",
              startDate = LocalDate.parse("2023-01-01"),
              expiryDate = null,
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentRestrictionTypes {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            restrictions = listOf(
              contactRestriction().copy(
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2023-01-01"),
              ),
            ),
          ),
          dpsContact,
          dpsContactRestrictions = listOf(
            contactRestrictionDetails().copy(
              restrictionType = "CCTV",
              startDate = LocalDate.parse("2023-01-01"),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenHasDifferentNumberOfRestriction {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            restrictions = listOf(
              contactRestriction().copy(
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2023-01-01"),
              ),
            ),
          ),
          dpsContact,
          dpsContactRestrictions = listOf(
            contactRestrictionDetails().copy(
              restrictionType = "BAN",
              startDate = LocalDate.parse("2023-01-01"),
            ),
            contactRestrictionDetails().copy(
              restrictionType = "CCTV",
              startDate = LocalDate.parse("2023-01-01"),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNotNull
      }

      @Test
      fun `telemetry will person details do not match`() = runTest {
        service.checkPersonContactMatch(personId)
        verify(telemetryClient).trackEvent(
          eq("contact-person-reconciliation-mismatch"),
          eq(
            mapOf(
              "personId" to "$personId",
              "reason" to "different-person-details",
            ),
          ),
          isNull(),
        )
      }
    }
  }
}
