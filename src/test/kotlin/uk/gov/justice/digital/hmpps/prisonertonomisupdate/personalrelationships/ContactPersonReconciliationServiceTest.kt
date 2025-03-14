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
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ContactForPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactSummaryPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.prisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.PrisonerContactSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

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
        dpsApi.stubGetPrisonerContacts(prisonerId.offenderNo, prisonerContactSummaryPage().copy(totalElements = 2, content = listOf(prisonerContactSummary(1), prisonerContactSummary(2))))
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
    inner class WhenOneIsNotOnCurrentTermBooking {
      @BeforeEach
      fun beforeEach() {
        stubContact(
          dpsContact = prisonerContactSummary().copy(
            currentTerm = true,
          ),
          nomisContact = prisonerContact().copy(
            bookingSequence = 2,
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
  }
}
