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
          lastBookingId = 3,
          prisonerIds = (1L..3).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      nomisApi.stubGetPrisonerContacts("A0001TZ", prisonerWithContacts().copy(contacts = listOf(prisonerContact(1), prisonerContact(2))))
      dpsApi.stubGetPrisonerContacts("A0001TZ", prisonerContactSummaryPage().copy(totalElements = 2, content = listOf(prisonerContactSummary(1))))
      nomisApi.stubGetPrisonerContacts("A0002TZ", prisonerWithContacts().copy(contacts = listOf(prisonerContact(1), prisonerContact(2))))
      dpsApi.stubGetPrisonerContacts("A0002TZ", prisonerContactSummaryPage().copy(totalElements = 2, content = listOf(prisonerContactSummary(1), prisonerContactSummary(99))))
      nomisApi.stubGetPrisonerContacts("A0003TZ", prisonerWithContacts())
      dpsApi.stubGetPrisonerContacts("A0003TZ", prisonerContactSummaryPage())
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
          assertThat(it).containsEntry("mismatch-count", "2")
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
            "contactId" to "2",
            "reason" to "different-contacts",
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
