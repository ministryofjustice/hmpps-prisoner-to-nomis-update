package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

@ExtendWith(MockitoExtension::class)
class SentencingResourceIntTest(
  @Autowired private val sentencingReconciliationService: SentencingReconciliationService,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @DisplayName("Sentencing reconciliation report")
  @Nested
  inner class GenerateSentencingReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      OFFENDER_NO

      val numberOfActivePrisoners = 34L
      nomisApi.stuGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(
          lastBookingId = 10,
          prisonerIds = (1L..10).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      nomisApi.stuGetAllLatestBookings(
        bookingId = 10,
        response = BookingIdsWithLast(
          lastBookingId = 20,
          prisonerIds = (11L..20).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      nomisApi.stuGetAllLatestBookings(
        bookingId = 20,
        response = BookingIdsWithLast(
          lastBookingId = 30,
          prisonerIds = (21L..30).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      nomisApi.stuGetAllLatestBookings(
        bookingId = 30,
        response = BookingIdsWithLast(
          lastBookingId = 34,
          prisonerIds = (31L..34).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )

      // mock non-matching for first and last prisoners
      nomisApi.stubGetSentencingAdjustments(1, SentencingAdjustmentsResponse(emptyList(), emptyList()))
      sentencingAdjustmentsApi.stubAdjustmentsGet("A0001TZ", listOf(adjustment(adjustmentType = AdjustmentDto.AdjustmentType.REMAND, bookingId = 1)))
      nomisApi.stubGetSentencingAdjustments(34, SentencingAdjustmentsResponse(emptyList(), listOf(sentenceAdjustment(adjustmentType = SentenceAdjustments.S240A))))
      sentencingAdjustmentsApi.stubAdjustmentsGet("A0034TZ", listOf(adjustment(adjustmentType = AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE, bookingId = 34)))
      // all others have no adjustments so match
      (2..<numberOfActivePrisoners).forEach {
        nomisApi.stubGetSentencingAdjustments(it, SentencingAdjustmentsResponse(emptyList(), emptyList()))
        sentencingAdjustmentsApi.stubAdjustmentsGet(generateOffenderNo(sequence = it), emptyList())
      }
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      sentencingReconciliationService.generateSentencingReconciliationReport()

      verify(telemetryClient).trackEvent(eq("sentencing-reports-reconciliation-requested"), any(), isNull())

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() = runTest {
      sentencingReconciliationService.generateSentencingReconciliationReport()

      awaitReportFinished()
      nomisApi.verify(
        // 34 prisoners will be spread over 4 pages of 10 prisoners each
        4,
        getRequestedFor(urlPathEqualTo("/bookings/ids/latest-from-id"))
          .withQueryParam("activeOnly", equalTo("true"))
          .withQueryParam("pageSize", equalTo("10")),
      )
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
      sentencingReconciliationService.generateSentencingReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("sentencing-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("A0001TZ", "0:1")
          assertThat(it).containsEntry("A0034TZ", "1:1")
        },
        isNull(),
      )

      verify(telemetryClient, times(2)).trackEvent(
        eq("sentencing-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      with(telemetryCaptor.allValues[0]) {
        assertThat(this).containsEntry("offenderNo", "A0001TZ")
        assertThat(this).containsEntry("bookingId", "1")
        assertThat(this).containsEntry("nomisAdjustmentsCount", "0")
        assertThat(this).containsEntry("dpsAdjustmentsCount", "1")
        assertThat(this).containsEntry("differences", "remand 1")
      }
      with(telemetryCaptor.allValues[1]) {
        assertThat(this).containsEntry("offenderNo", "A0034TZ")
        assertThat(this).containsEntry("bookingId", "34")
        assertThat(this).containsEntry("nomisAdjustmentsCount", "1")
        assertThat(this).containsEntry("dpsAdjustmentsCount", "1")
        assertThat(this).containsEntry("differences", "unlawfullyAtLarge 1, taggedBail -1")
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentsGetWithError("A0001TZ", 500)

      sentencingReconciliationService.generateSentencingReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(1)).trackEvent(
        eq("sentencing-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("sentencing-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "1")
          assertThat(it).containsEntry("A0034TZ", "1:1")
        },
        isNull(),
      )
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() = runTest {
      nomisApi.stuGetAllLatestBookings(
        bookingId = 10,
        errorStatus = HttpStatus.INTERNAL_SERVER_ERROR,
      )

      sentencingReconciliationService.generateSentencingReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("sentencing-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("sentencing-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("A0001TZ", "0:1")
          assertThat(it).containsEntry("A0034TZ", "1:1")
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("sentencing-reports-reconciliation-report"), any(), isNull()) }
  }
}
