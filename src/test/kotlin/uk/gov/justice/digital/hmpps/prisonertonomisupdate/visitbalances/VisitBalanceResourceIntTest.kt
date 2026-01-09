package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import kotlin.collections.map

@ExtendWith(MockitoExtension::class)
class VisitBalanceResourceIntTest(
  @Autowired private val visitBalanceReconciliationService: VisitBalanceReconciliationService,
  @Autowired private val visitBalanceNomisApi: VisitBalanceNomisApiMockServer,
) : IntegrationTestBase() {

  private val visitBalanceDpsApi = VisitBalanceDpsApiExtension.Companion.visitBalanceDpsApi

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @DisplayName("PUT /visit-balance/reports/reconciliation")
  @Nested
  inner class GenerateVisitBalanceReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)

      nomisApi.stuGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(
          lastBookingId = 10,
          prisonerIds = (1L..10L).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      nomisApi.stuGetAllLatestBookings(
        bookingId = 10,
        response = BookingIdsWithLast(
          lastBookingId = 20,
          prisonerIds = (11L..20L).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      nomisApi.stuGetAllLatestBookings(
        bookingId = 20,
        response = BookingIdsWithLast(
          lastBookingId = 30,
          prisonerIds = (21L..30L).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      nomisApi.stuGetAllLatestBookings(
        bookingId = 30,
        response = BookingIdsWithLast(
          lastBookingId = 34,
          prisonerIds = (31L..34L).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )

      // mock non-matching for first, second and last prisoners
      visitBalanceNomisApi.stubGetVisitBalance("A0001TZ", visitBalance().copy(remainingVisitOrders = 7))
      visitBalanceDpsApi.stubGetVisitBalance(PrisonerBalanceDto(prisonerId = "A0001TZ", voBalance = 12, pvoBalance = 9))

      visitBalanceNomisApi.stubGetVisitBalance("A0002TZ", visitBalance().copy(remainingVisitOrders = 4))
      visitBalanceDpsApi.stubGetVisitBalance(PrisonerBalanceDto(prisonerId = "A0002TZ", voBalance = 9, pvoBalance = 7))

      visitBalanceNomisApi.stubGetVisitBalance("A0034TZ", visitBalance().copy(remainingVisitOrders = 2))
      visitBalanceDpsApi.stubGetVisitBalance(PrisonerBalanceDto(prisonerId = "A0034TZ", voBalance = 17, pvoBalance = 8))

      // all others are ok
      (3L..<34L).forEach {
        val offenderNo = generateOffenderNo(sequence = it)
        visitBalanceNomisApi.stubGetVisitBalance(offenderNo)
        visitBalanceDpsApi.stubGetVisitBalance(visitBalanceDto().copy(prisonerId = offenderNo))
      }
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      visitBalanceReconciliationService.generateReconciliationReport()

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-requested"),
        check { assertThat(it).isEmpty() },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() = runTest {
      visitBalanceReconciliationService.generateReconciliationReport()

      awaitReportFinished()
      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", WireMock.equalTo("0")),
      )
      nomisApi.verify(
        // 34 prisoners will be spread over 4 pages of 10 prisoners each
        WireMock.getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", WireMock.equalTo("10")),
      )
      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", WireMock.equalTo("20")),
      )
      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("bookingId", WireMock.equalTo("30")),
      )
      nomisApi.checkForUnmatchedRequests()
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
      visitBalanceReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("visitbalance-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      val mismatchedRecords = telemetryCaptor.allValues.map { it["prisonNumber"] }

      assertThat(mismatchedRecords).containsOnly("A0001TZ", "A0002TZ", "A0034TZ")
      with(telemetryCaptor.allValues.find { it["prisonNumber"] == "A0001TZ" }) {
        assertThat(this).containsEntry("bookingId", "1")
        assertThat(this).containsEntry("nomisVisitBalance", "7")
        assertThat(this).containsEntry("dpsVisitBalance", "12")
        assertThat(this).containsEntry("nomisPrivilegedVisitBalance", "3")
        assertThat(this).containsEntry("dpsPrivilegedVisitBalance", "9")
      }
      with(telemetryCaptor.allValues.find { it["prisonNumber"] == "A0002TZ" }) {
        assertThat(this).containsEntry("bookingId", "2")
        assertThat(this).containsEntry("nomisVisitBalance", "4")
        assertThat(this).containsEntry("dpsVisitBalance", "9")
        assertThat(this).containsEntry("nomisPrivilegedVisitBalance", "3")
        assertThat(this).containsEntry("dpsPrivilegedVisitBalance", "7")
      }
      with(telemetryCaptor.allValues.find { it["prisonNumber"] == "A0034TZ" }) {
        assertThat(this).containsEntry("bookingId", "34")
        assertThat(this).containsEntry("nomisVisitBalance", "2")
        assertThat(this).containsEntry("nomisPrivilegedVisitBalance", "3")
        assertThat(this).containsEntry("dpsVisitBalance", "17")
        assertThat(this).containsEntry("dpsPrivilegedVisitBalance", "8")
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      visitBalanceDpsApi.stubGetVisitBalance("A0002TZ", HttpStatus.INTERNAL_SERVER_ERROR)

      visitBalanceReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(1)).trackEvent(
        eq("visitbalance-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
        },
        isNull(),
      )
    }

    @Test
    fun `will complete a report even if a visit balance from Nomis does not exist`() = runTest {
      visitBalanceNomisApi.stubGetVisitBalance("A0002TZ", HttpStatus.NOT_FOUND)

      visitBalanceReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(0)).trackEvent(
        eq("visitbalance-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )
    }

    @Test
    fun `will complete a report even if a visit balance from dps does not exist`() = runTest {
      visitBalanceDpsApi.stubGetVisitBalance("A0002TZ", HttpStatus.NOT_FOUND)

      visitBalanceReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(0)).trackEvent(
        eq("visitbalance-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )
    }

    @Test
    fun `will complete a report even if a visit balance from both nomis and dps do not exist`() = runTest {
      visitBalanceNomisApi.stubGetVisitBalance("A0002TZ", HttpStatus.NOT_FOUND)
      visitBalanceDpsApi.stubGetVisitBalance("A0002TZ", HttpStatus.NOT_FOUND)

      visitBalanceReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(0)).trackEvent(
        eq("visitbalance-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
        },
        isNull(),
      )
    }

    @Test
    fun `will attempt to complete a report even if the first page fails`() = runTest {
      nomisApi.stuGetAllLatestBookings(bookingId = 0, errorStatus = HttpStatus.INTERNAL_SERVER_ERROR)

      visitBalanceReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() = runTest {
      nomisApi.stuGetAllLatestBookings(bookingId = 20, errorStatus = HttpStatus.INTERNAL_SERVER_ERROR)

      visitBalanceReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("visitbalance-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
        },
        isNull(),
      )
    }
  }

  @DisplayName("GET /visit-balance/reconciliation/{prisonNumber}")
  @Nested
  inner class GenerateReconciliationReportForPrisoner {
    private val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/visit-balance/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/visit-balance/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/visit-balance/reconciliation/$prisonNumber")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `return null when offender not found`() {
        visitBalanceNomisApi.stubGetVisitBalance("A9999BC", HttpStatus.NOT_FOUND)
        webTestClient.get().uri("/visit-balance/reconciliation/A9999BC")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody().isEmpty
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setup() {
        visitBalanceNomisApi.stubGetVisitBalance()
        visitBalanceDpsApi.stubGetVisitBalance()
      }

      @Test
      fun `will return no differences`() {
        webTestClient.get().uri("/visit-balance/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return mismatch with nomis`() {
        visitBalanceNomisApi.stubGetVisitBalance(prisonNumber, visitBalance().copy(remainingVisitOrders = 4))

        webTestClient.get().uri("/visit-balance/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("prisonNumber").isEqualTo(prisonNumber)
          .jsonPath("nomisVisitBalance.visitBalance").isEqualTo(4)
          .jsonPath("nomisVisitBalance.privilegedVisitBalance").isEqualTo(3)
          .jsonPath("dpsVisitBalance.visitBalance").isEqualTo(24)
          .jsonPath("dpsVisitBalance.privilegedVisitBalance").isEqualTo(3)

        verify(telemetryClient).trackEvent(
          eq("visitbalance-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("visitbalance-reports-reconciliation-report"), any(), isNull()) }
  }
}
