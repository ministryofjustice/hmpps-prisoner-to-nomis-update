package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.contactReconcileDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRelationshipDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestrictionDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.contactPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.pagePersonIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.prisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.util.Collections.singletonList

class ContactPersonReconciliationResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: ContactPersonNomisApiMockServer

  private val dpsApi = ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
  private val nomisPrisonerApi = NomisApiExtension.Companion.nomisApi

  @DisplayName("PUT /contact-person/prisoner-contact/reports/reconciliation")
  @Nested
  inner class GeneratePrisonerContactReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisPrisonerApi.stuGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(
          lastBookingId = 4,
          prisonerIds = (1L..4).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      nomisApi.stubGetPrisonerContacts("A0001TZ", prisonerWithContacts().copy(contacts = listOf(prisonerContact(1), prisonerContact(2))))
      dpsApi.stubGetPrisonerContacts("A0001TZ", prisonerContactDetails().copy(relationships = listOf(prisonerContactRelationshipDetails(1))))
      nomisApi.stubGetPrisonerContacts("A0002TZ", prisonerWithContacts().copy(contacts = listOf(prisonerContact(1), prisonerContact(2))))
      dpsApi.stubGetPrisonerContacts("A0002TZ", prisonerContactDetails().copy(relationships = listOf(prisonerContactRelationshipDetails(1), prisonerContactRelationshipDetails(99))))
      nomisApi.stubGetPrisonerContacts("A0003TZ", prisonerWithContacts())
      dpsApi.stubGetPrisonerContacts("A0003TZ", prisonerContactDetails().copy(relationships = emptyList()))
      nomisApi.stubGetPrisonerContacts("A0004TZ", prisonerWithContacts().copy(contacts = listOf(prisonerContact(3).copy(restrictions = emptyList()))))
      dpsApi.stubGetPrisonerContacts("A0004TZ", prisonerContactDetails().copy(relationships = listOf(prisonerContactRelationshipDetails(contactId = 3, prisonerContactId = 30).copy(restrictions = listOf(prisonerContactRestrictionDetails())))))
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/contact-person/prisoner-contact/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-contact-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output mismatch report`() {
      webTestClient.put().uri("/contact-person/prisoner-contact/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-contact-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there is a missing DPS record`() {
      webTestClient.put().uri("/contact-person/prisoner-contact/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-contact-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0001TZ",
            "dpsContactCount" to "1",
            "nomisContactCount" to "2",
            "contactIdsMissingFromNomis" to "[]",
            "contactIdsMissingFromDps" to "[2]",
            "reason" to "different-number-of-contacts",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there are different people`() {
      webTestClient.put().uri("/contact-person/prisoner-contact/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-contact-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0002TZ",
            "dpsContactCount" to "2",
            "nomisContactCount" to "2",
            "contactIdsMissingFromNomis" to "[99]",
            "contactIdsMissingFromDps" to "[2]",
            "reason" to "different-contacts",
          ),
        ),
        isNull(),
      )
    }

    @Test
    fun `will output a mismatch when there are different restrictions for a contact`() {
      webTestClient.put().uri("/contact-person/prisoner-contact/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-prisoner-contact-reconciliation-mismatch"),
        eq(
          mapOf(
            "offenderNo" to "A0004TZ",
            "contactId" to "3",
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

    private fun awaitReportFinished() {
      await untilAsserted { verify(telemetryClient).trackEvent(eq("contact-person-prisoner-contact-reconciliation-report"), any(), isNull()) }
    }
  }

  @DisplayName("PUT /contact-person/person-contact/reports/reconciliation")
  @Nested
  inner class GeneratePersonContactReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetPersonIdsTotals(pagePersonIdResponse(100))
      dpsApi.stubGetContactIds(contactIds = singletonList(1), totalElements = 99)

      // stub 10 pages of 10 personIds
      (0L..90).step(10).forEach {
        nomisApi.stubGetPersonIds(
          lastPersonId = it,
          response = PersonIdsWithLast(
            lastPersonId = it + 10,
            personIds = (it + 1..it + 10).toList(),
          ),
        )
      }
      nomisApi.stubGetPersonIds(lastPersonId = 100, response = PersonIdsWithLast(lastPersonId = 0, personIds = emptyList()))

      // stub 99 matched contacts
      (1L..99).forEach {
        nomisApi.stubGetPerson(
          it,
          contactPerson(personId = it).copy(
            firstName = "KWEKU", lastName = "KOFI", phoneNumbers = emptyList(),
            employments = emptyList(),
            identifiers = emptyList(),
            addresses = emptyList(),
            emailAddresses = emptyList(),
            contacts = emptyList(),
            restrictions = emptyList(),
          ),
        )
        dpsApi.stubGetContactDetails(it, contactReconcileDetails(contactId = it).copy(firstName = "KWEKU", lastName = "KOFI"))
      }
      // final one mismatched
      nomisApi.stubGetPerson(100, contactPerson(personId = 100).copy(firstName = "JANE", lastName = "SMITH"))
      dpsApi.stubGetContactDetails(100, null)
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/contact-person/person-contact/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(
        eq("contact-person-reconciliation-requested"),
        any(),
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will output mismatch report`() {
      webTestClient.put().uri("/contact-person/person-contact/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("pages-count", "10")
          assertThat(it).containsEntry("contacts-count", "100")
        },
        isNull(),
      )
    }

    @Test
    fun `will output totals mismatch telemetry`() {
      webTestClient.put().uri("/contact-person/person-contact/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted
      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("contact-person-reconciliation-mismatch-totals"),
        check {
          assertThat(it).containsEntry("nomisTotal", "100")
          assertThat(it).containsEntry("dpsTotal", "99")
        },
        isNull(),
      )
    }
  }

  @DisplayName("GET /persons/{personId}/person-contact/reconciliation")
  @Nested
  inner class GetPersonContactReconciliationForPerson {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      nomisApi.stubGetPerson(
        1,
        contactPerson(personId = 1).copy(
          firstName = "KWEKU",
          lastName = "KOFI",
          phoneNumbers = emptyList(),
          employments = emptyList(),
          identifiers = emptyList(),
          addresses = emptyList(),
          emailAddresses = emptyList(),
          contacts = emptyList(),
          restrictions = emptyList(),
        ),
      )
      dpsApi.stubGetContactDetails(1, contactReconcileDetails(contactId = 1).copy(firstName = "JOHN", lastName = "KOFI"))
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/persons/1/person-contact/reconciliation")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/persons/1/person-contact/reconciliation")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/persons/1/person-contact/reconciliation")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `will return mismatch`() {
        webTestClient.get().uri("/persons/1/person-contact/reconciliation")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.personId").isEqualTo(1)
          .jsonPath("$.dpsSummary.firstName").isEqualTo("JOHN")
          .jsonPath("$.nomisSummary.firstName").isEqualTo("KWEKU")
      }
    }
  }
  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("contact-person-reconciliation-report"), any(), isNull()) }
  }
}
