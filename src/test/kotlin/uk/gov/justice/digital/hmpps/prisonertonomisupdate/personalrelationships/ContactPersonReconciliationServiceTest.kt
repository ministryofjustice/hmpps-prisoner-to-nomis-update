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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmailDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactEmploymentDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactIdentityDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactPhoneDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactReconcileDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactRestrictionDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.linkedPrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRelationshipDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestrictionDetails
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.ReconcilePrisonerRelationship
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncContactReconcile
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
    private fun stubContact(dpsContact: ReconcilePrisonerRelationship, nomisContact: PrisonerContact) {
      nomisApi.stubGetPrisonerContacts(
        prisonerId.offenderNo,
        prisonerWithContacts().copy(
          contacts = listOf(nomisContact),
        ),
      )
      dpsApi.stubGetPrisonerContacts(
        prisonerId.offenderNo,
        prisonerContactDetails().copy(
          relationships = listOf(dpsContact),
        ),
      )
    }

    @Nested
    inner class WhenPrisonerHasNoContacts {
      @BeforeEach
      fun beforeEach() {
        nomisApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerWithContacts().copy(contacts = emptyList()))
        dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactDetails().copy(relationships = emptyList()))
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
        dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactDetails().copy(relationships = listOf(prisonerContactRelationshipDetails(contactId = 1, prisonerContactId = 11), prisonerContactRelationshipDetails(contactId = 2, prisonerContactId = 22))))
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkPrisonerContactsMatch(prisonerId),
        ).isNull()
      }
    }

    @Nested
    inner class WhenPrisonerHasSameContactsButWithDifferentCase {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactRelationshipDetails().copy(
            contactId = 99,
            firstName = "Jane",
            lastName = "Smith",
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
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkPrisonerContactsMatch(prisonerId),
        ).isNull()
      }
    }

    @Nested
    inner class WhenPrisonerHasSameContactsButWithSpaces {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactRelationshipDetails().copy(
            contactId = 99,
            firstName = "Jane",
            lastName = "Smith",
          ),
          nomisContact = prisonerContact().copy(
            person = ContactForPerson(
              personId = 99,
              firstName = " JANE",
              lastName = "SMITH ",
            ),
          ),
        )
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
          prisonerContactDetails().copy(
            relationships = listOf(
              prisonerContactRelationshipDetails(
                contactId = 1,
                prisonerContactId = 11,
              ).copy(
                approvedVisitor = false,
              ),
              prisonerContactRelationshipDetails(
                contactId = 1,
                prisonerContactId = 22,
              ).copy(
                approvedVisitor = true,
              ),
            ),
          ),
        )
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
        dpsApi.stubGetPrisonerContacts(
          prisonerId.offenderNo,
          prisonerContactDetails().copy(
            relationships = emptyList(),
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
        dpsApi.stubGetPrisonerContacts(
          prisonerId.offenderNo,
          prisonerContactDetails().copy(
            relationships = listOf(prisonerContactRelationshipDetails(contactId = 1)),
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
        dpsApi.stubGetPrisonerContacts(
          prisonerId.offenderNo,
          prisonerContactDetails().copy(
            relationships = listOf(prisonerContactRelationshipDetails(contactId = 99)),
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
          dpsContact = prisonerContactRelationshipDetails().copy(
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
      fun `telemetry will show contact is different`() = runTest {
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
          dpsContact = prisonerContactRelationshipDetails().copy(
            approvedVisitor = true,
          ),
          nomisContact = prisonerContact().copy(
            approvedVisitor = false,
          ),
        )
      }

      @Test
      fun `telemetry will show contact is different`() = runTest {
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
          dpsContact = prisonerContactRelationshipDetails().copy(
            relationshipTypeCode = "S",
          ),
          nomisContact = prisonerContact().copy(
            contactType = CodeDescription("O", "Official"),
          ),
        )
      }

      @Test
      fun `telemetry will show contact is different`() = runTest {
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
          dpsContact = prisonerContactRelationshipDetails().copy(
            relationshipToPrisoner = "BRO",
          ),
          nomisContact = prisonerContact().copy(
            relationshipType = CodeDescription("FRI", "Friend"),
          ),
        )
      }

      @Test
      fun `telemetry will show contact is different`() = runTest {
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
          dpsContact = prisonerContactRelationshipDetails().copy(
            active = true,
          ),
          nomisContact = prisonerContact().copy(
            active = false,
          ),
        )
      }

      @Test
      fun `telemetry will show contact is different`() = runTest {
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
    inner class WhenOneIsEmergencyContactButNotTheOther {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactRelationshipDetails().copy(
            emergencyContact = true,
          ),
          nomisContact = prisonerContact().copy(
            emergencyContact = false,
          ),
        )
      }

      @Test
      fun `telemetry will show contact is different`() = runTest {
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
          dpsContact = prisonerContactRelationshipDetails().copy(
            nextOfKin = true,
          ),
          nomisContact = prisonerContact().copy(
            nextOfKin = false,
          ),
        )
      }

      @Test
      fun `telemetry will show contact is different`() = runTest {
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
          dpsApi.stubGetPrisonerContacts(
            prisonerId.offenderNo,
            prisonerContactDetails().copy(
              relationships = listOf(prisonerContactRelationshipDetails(contactId = 1, prisonerContactId = 11).copy(restrictions = emptyList())),
            ),
          )
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
          dpsApi.stubGetPrisonerContacts(
            prisonerId.offenderNo,
            prisonerContactDetails().copy(
              relationships = listOf(prisonerContactRelationshipDetails(contactId = 1, prisonerContactId = 11).copy(restrictions = listOf(prisonerContactRestrictionDetails()))),
            ),
          )
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
          dpsApi.stubGetPrisonerContacts(
            prisonerId.offenderNo,
            prisonerContactDetails().copy(
              relationships = listOf(prisonerContactRelationshipDetails(contactId = 1, prisonerContactId = 11).copy(restrictions = emptyList())),
            ),
          )
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
          dpsApi.stubGetPrisonerContacts(
            prisonerId.offenderNo,
            prisonerContactDetails().copy(
              relationships = listOf(prisonerContactRelationshipDetails(contactId = 1, prisonerContactId = 11).copy(restrictions = listOf(prisonerContactRestrictionDetails()))),
            ),
          )
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
          dpsApi.stubGetPrisonerContacts(
            prisonerId.offenderNo,
            prisonerContactDetails().copy(
              relationships = listOf(prisonerContactRelationshipDetails(contactId = 1, prisonerContactId = 11).copy(restrictions = listOf(prisonerContactRestrictionDetails().copy(restrictionType = "BAN")))),
            ),
          )
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
          dpsApi.stubGetPrisonerContacts(
            prisonerId.offenderNo,
            prisonerContactDetails().copy(
              relationships = listOf(prisonerContactRelationshipDetails(contactId = 1, prisonerContactId = 11).copy(restrictions = listOf(prisonerContactRestrictionDetails().copy(expiryDate = LocalDate.parse("2022-01-01"))))),
            ),
          )
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
    val dpsContact = contactReconcileDetails(contactId = personId).copy(
      firstName = "KWEKU",
      lastName = "KOFI",
    )

    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
    }

    private fun stubPersonContact(nomisPerson: ContactPerson, dpsContact: SyncContactReconcile) {
      nomisApi.stubGetPerson(personId, nomisPerson)
      dpsApi.stubGetContactDetails(personId, dpsContact)
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
            phones = listOf(contactPhoneDetails("0114555555")),
            addresses = listOf(contactAddressDetails(phoneNumbers = listOf(contactAddressPhoneDetails("01146666666")))),
            emails = listOf(contactEmailDetails("test@justice.gov.uk")),
            employments = listOf(contactEmploymentDetails(98765)),
            identities = listOf(contactIdentityDetails("SMITH1717171")),
            restrictions = listOf(
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
            relationships = listOf(
              linkedPrisonerDetails().copy(
                prisonerNumber = "A1234KT",
                relationshipType = "BRO",
                active = false,
              ),
              linkedPrisonerDetails().copy(
                prisonerNumber = "A1234KT",
                relationshipType = "FRI",
                active = true,
              ),
              linkedPrisonerDetails().copy(
                prisonerNumber = "A1000KT",
                relationshipType = "GIR",
                active = true,
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
    inner class WhenNamesHaveDifferentCase {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            firstName = "KWEKU",
            lastName = "KOFI",
          ),
          dpsContact.copy(
            firstName = "Kweku",
            lastName = "Kofi",
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNull()
      }
    }

    @Nested
    inner class WhenNamesHaveSpaces {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            firstName = "KWEKU",
            lastName = "KOFI ",
            middleName = "HARRY ",
          ),
          dpsContact.copy(
            firstName = "Kweku",
            lastName = "Kofi",
            middleNames = "Harry",
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNull()
      }
    }

    @Nested
    inner class WhenDateOfBirthsAreClearlyNotValid {

      @BeforeEach
      fun setUp() {
        stubPersonContact(
          nomisPerson.copy(
            dateOfBirth = LocalDate.parse("0200-02-20"),
          ),
          dpsContact.copy(
            dateOfBirth = LocalDate.parse("0200-02-21"),
          ),
        )
      }

      @Test
      fun `will ignore the mismatch`() = runTest {
        assertThat(service.checkPersonContactMatch(personId)).isNull()
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
            phones = listOf(contactPhoneDetails("01146666666")),
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
            emails = listOf(contactEmailDetails("bob@justice.gov.uk")),
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
          dpsContact.copy(relationships = listOf(linkedPrisonerDetails().copy(prisonerNumber = "A1234KT", relationshipType = "BRO", active = true))),
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
          dpsContact.copy(relationships = listOf(linkedPrisonerDetails().copy(prisonerNumber = "A1234KT", relationshipType = "BRO"))),
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
          dpsContact.copy(relationships = listOf(linkedPrisonerDetails().copy(prisonerNumber = "A1234KT", relationshipType = "BRO"))),
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
          dpsContact.copy(
            restrictions = listOf(
              contactRestrictionDetails().copy(
                restrictionType = "BAN",
                startDate = LocalDate.parse("2023-01-01"),
                expiryDate = null,
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
          dpsContact.copy(
            restrictions = listOf(
              contactRestrictionDetails().copy(
                restrictionType = "CCTV",
                startDate = LocalDate.parse("2023-01-01"),
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
          dpsContact.copy(
            restrictions = listOf(
              contactRestrictionDetails().copy(
                restrictionType = "BAN",
                startDate = LocalDate.parse("2023-01-01"),
              ),
              contactRestrictionDetails().copy(
                restrictionType = "CCTV",
                startDate = LocalDate.parse("2023-01-01"),
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
  }
}
