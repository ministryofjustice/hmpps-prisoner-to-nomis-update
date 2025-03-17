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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestrictionDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactRestrictionsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerContactSummaryPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.prisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

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
      dpsApi.stubGetPrisonerContacts("A0001TZ", prisonerContactSummaryPage().copy(totalElements = 2, content = listOf(prisonerContactSummary(1))))
      nomisApi.stubGetPrisonerContacts("A0002TZ", prisonerWithContacts().copy(contacts = listOf(prisonerContact(1), prisonerContact(2))))
      dpsApi.stubGetPrisonerContacts("A0002TZ", prisonerContactSummaryPage().copy(totalElements = 2, content = listOf(prisonerContactSummary(1), prisonerContactSummary(99))))
      nomisApi.stubGetPrisonerContacts("A0003TZ", prisonerWithContacts())
      dpsApi.stubGetPrisonerContacts("A0003TZ", prisonerContactSummaryPage())
      nomisApi.stubGetPrisonerContacts("A0004TZ", prisonerWithContacts().copy(contacts = listOf(prisonerContact(3).copy(restrictions = emptyList()))))
      dpsApi.stubGetPrisonerContacts(
        "A0004TZ",
        prisonerContactSummaryPage().copy(
          totalElements = 1,
          content = listOf(
            prisonerContactSummary(
              id = 3,
              prisonerContactId = 30,
            ),
          ),
        ),
      )
      dpsApi.stubGetPrisonerContactRestrictions(30, prisonerContactRestrictionsResponse().copy(prisonerContactRestrictions = listOf(prisonerContactRestrictionDetails(contactId = 3))))
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
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("contact-person-prisoner-contact-reconciliation-report"), any(), isNull()) }
  }
}
