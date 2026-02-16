@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.KeyDateAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentencingAdjustmentType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto.AdjustmentType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringAPIServiceTest
@Import(
  SentencingReconciliationService::class,
  NomisApiService::class,
  SentencingAdjustmentsApiService::class,
  SentencingConfiguration::class,
  RetryApiService::class,
)
internal class SentencingReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var service: SentencingReconciliationService

  @Nested
  inner class CheckBookingAdjustmentsMatch {

    @Nested
    inner class WhenBothSystemsHaveNoAdjustments {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(emptyList())
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = emptyList(),
            sentenceAdjustments = emptyList(),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveJustZeroDayAdjustments {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(listOf(adjustment(AdjustmentType.REMAND, effectiveDays = 0)))
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = emptyList(),
            sentenceAdjustments = listOf(sentenceAdjustment(adjustmentType = SentenceAdjustments.RX, effectiveDays = 0)),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveOneOfEachAdjustmentType {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(
          listOf(
            adjustment(AdjustmentType.LAWFULLY_AT_LARGE),
            adjustment(AdjustmentType.UNLAWFULLY_AT_LARGE),
            adjustment(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED),
            adjustment(AdjustmentType.ADDITIONAL_DAYS_AWARDED),
            adjustment(AdjustmentType.SPECIAL_REMISSION),
            adjustment(AdjustmentType.REMAND),
            adjustment(AdjustmentType.UNUSED_DEDUCTIONS),
            adjustment(AdjustmentType.TAGGED_BAIL),
            adjustment(AdjustmentType.TAGGED_BAIL),
            adjustment(AdjustmentType.REMAND),
          ),
        )
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = listOf(
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.LAL),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.UAL),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.RADA),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.ADA),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.SREM),
            ),
            sentenceAdjustments = listOf(
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RSR),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.UR),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.S240A),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RST),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RX),
            ),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = "offenderNo",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveVariousTaggedBailAdjustmentsThatMatch {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(
          listOf(
            adjustment(AdjustmentType.TAGGED_BAIL),
            adjustment(AdjustmentType.TAGGED_BAIL),
          ),
        )
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = listOf(),
            sentenceAdjustments = listOf(
              sentenceAdjustment(adjustmentType = SentenceAdjustments.S240A),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RST),
            ),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveVariousRemandAdjustmentsThatMatch {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(
          listOf(
            adjustment(AdjustmentType.REMAND),
            adjustment(AdjustmentType.REMAND),
          ),
        )
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = listOf(),
            sentenceAdjustments = listOf(
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RSR),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RX),
            ),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenDPSHaveOneAdjustmentMissing {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(
          listOf(
            adjustment(AdjustmentType.LAWFULLY_AT_LARGE),
            adjustment(AdjustmentType.UNLAWFULLY_AT_LARGE),
            adjustment(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED),
            adjustment(AdjustmentType.ADDITIONAL_DAYS_AWARDED),
            adjustment(AdjustmentType.SPECIAL_REMISSION),
            adjustment(AdjustmentType.REMAND),
            adjustment(AdjustmentType.UNUSED_DEDUCTIONS),
            adjustment(AdjustmentType.TAGGED_BAIL),
            adjustment(AdjustmentType.TAGGED_BAIL),
          ),
        )
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = listOf(
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.LAL),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.UAL),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.RADA),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.ADA),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.SREM),
            ),
            sentenceAdjustments = listOf(
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RSR),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.UR),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.S240A),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RST),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RX),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
          ),
        ).isEqualTo(
          MismatchSentencingAdjustments(
            prisonerId = PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
            dpsCounts = AdjustmentCounts(
              lawfullyAtLarge = 1,
              unlawfullyAtLarge = 1,
              restorationOfAdditionalDaysAwarded = 1,
              additionalDaysAwarded = 1,
              specialRemission = 1,
              remand = 1,
              unusedDeductions = 1,
              taggedBail = 2,
            ),
            nomisCounts = AdjustmentCounts(
              lawfullyAtLarge = 1,
              unlawfullyAtLarge = 1,
              restorationOfAdditionalDaysAwarded = 1,
              additionalDaysAwarded = 1,
              specialRemission = 1,
              remand = 2,
              unusedDeductions = 1,
              taggedBail = 2,
            ),
          ),
        )
      }
    }

    @Nested
    inner class WhenDPSHasAnAdditionalZeroDayAdjustment {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(
          listOf(
            adjustment(AdjustmentType.LAWFULLY_AT_LARGE),
            adjustment(AdjustmentType.ADDITIONAL_DAYS_AWARDED, effectiveDays = 0),
          ),
        )
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = listOf(
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.LAL),
            ),
            sentenceAdjustments = listOf(),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenNOMISHaveOneAdjustmentMissing {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(
          listOf(
            adjustment(AdjustmentType.LAWFULLY_AT_LARGE),
            adjustment(AdjustmentType.UNLAWFULLY_AT_LARGE),
            adjustment(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED),
            adjustment(AdjustmentType.ADDITIONAL_DAYS_AWARDED),
            adjustment(AdjustmentType.SPECIAL_REMISSION),
            adjustment(AdjustmentType.REMAND),
            adjustment(AdjustmentType.UNUSED_DEDUCTIONS),
            adjustment(AdjustmentType.TAGGED_BAIL),
            adjustment(AdjustmentType.TAGGED_BAIL),
            adjustment(AdjustmentType.REMAND),
          ),
        )
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = listOf(
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.LAL),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.UAL),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.RADA),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.ADA),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.SREM),
            ),
            sentenceAdjustments = listOf(
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RSR),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.UR),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.S240A),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RST),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
          ),
        ).isEqualTo(
          MismatchSentencingAdjustments(
            prisonerId = PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
            dpsCounts = AdjustmentCounts(
              lawfullyAtLarge = 1,
              unlawfullyAtLarge = 1,
              restorationOfAdditionalDaysAwarded = 1,
              additionalDaysAwarded = 1,
              specialRemission = 1,
              remand = 2,
              unusedDeductions = 1,
              taggedBail = 2,
            ),
            nomisCounts = AdjustmentCounts(
              lawfullyAtLarge = 1,
              unlawfullyAtLarge = 1,
              restorationOfAdditionalDaysAwarded = 1,
              additionalDaysAwarded = 1,
              specialRemission = 1,
              remand = 1,
              unusedDeductions = 1,
              taggedBail = 2,
            ),
          ),
        )
      }
    }

    @Nested
    inner class WhenBothSystemsHaveSameNumberButDifferentAdjustments {
      @BeforeEach
      fun beforeEach() {
        sentencingAdjustmentsApi.stubAdjustmentsGet(
          listOf(
            adjustment(AdjustmentType.LAWFULLY_AT_LARGE),
            adjustment(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED),
            adjustment(AdjustmentType.ADDITIONAL_DAYS_AWARDED),
            adjustment(AdjustmentType.REMAND),
            adjustment(AdjustmentType.TAGGED_BAIL),
          ),
        )
        nomisApi.stubGetSentencingAdjustments(
          bookingId = 123456,
          sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
            keyDateAdjustments = listOf(
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.UAL),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.RADA),
              keyDateAdjustment(adjustmentType = KeyDateAdjustments.ADA),
            ),
            sentenceAdjustments = listOf(
              sentenceAdjustment(adjustmentType = SentenceAdjustments.RSR),
              sentenceAdjustment(adjustmentType = SentenceAdjustments.S240A),
            ),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        assertThat(
          service.checkBookingAdjustmentsMatch(
            PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
          ),
        ).isEqualTo(
          MismatchSentencingAdjustments(
            prisonerId = PrisonerIds(
              bookingId = ADJUSTMENT_BOOKING_ID,
              offenderNo = OFFENDER_NO,
            ),
            dpsCounts = AdjustmentCounts(
              lawfullyAtLarge = 1,
              unlawfullyAtLarge = 0,
              restorationOfAdditionalDaysAwarded = 1,
              additionalDaysAwarded = 1,
              specialRemission = 0,
              remand = 1,
              unusedDeductions = 0,
              taggedBail = 1,
            ),
            nomisCounts = AdjustmentCounts(
              lawfullyAtLarge = 0,
              unlawfullyAtLarge = 1,
              restorationOfAdditionalDaysAwarded = 1,
              additionalDaysAwarded = 1,
              specialRemission = 0,
              remand = 1,
              unusedDeductions = 0,
              taggedBail = 1,
            ),
          ),
        )
      }
    }
  }

  @Nested
  inner class GenerateReconciliationReport {
    @BeforeEach
    fun setUp() {
      nomisApi.stuGetAllLatestBookings(
        response = BookingIdsWithLast(
          lastBookingId = 4,
          prisonerIds = (1L..4).map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) },
        ),
      )
      // different adjustments
      sentencingAdjustmentsApi.stubAdjustmentsGet(
        offenderNo = "A0001TZ",
        listOf(
          adjustment(AdjustmentType.LAWFULLY_AT_LARGE, bookingId = 1),
        ),
      )
      nomisApi.stubGetSentencingAdjustments(
        bookingId = 1,
        sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
          keyDateAdjustments = listOf(
            keyDateAdjustment(adjustmentType = KeyDateAdjustments.RADA),
          ),
          sentenceAdjustments = listOf(
            sentenceAdjustment(adjustmentType = SentenceAdjustments.RX),
          ),
        ),
      )
      // same adjustments
      sentencingAdjustmentsApi.stubAdjustmentsGet(
        offenderNo = "A0002TZ",
        listOf(
          adjustment(AdjustmentType.UNLAWFULLY_AT_LARGE, bookingId = 2),
        ),
      )
      nomisApi.stubGetSentencingAdjustments(
        bookingId = 2,
        sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
          keyDateAdjustments = listOf(
            keyDateAdjustment(adjustmentType = KeyDateAdjustments.UAL),
          ),
          sentenceAdjustments = listOf(),
        ),
      )
      // none and missing from DPS
      sentencingAdjustmentsApi.stubAdjustmentsGetWithError(
        offenderNo = "A0003TZ",
        status = 404,
      )
      nomisApi.stubGetSentencingAdjustments(
        bookingId = 3,
        sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
          keyDateAdjustments = listOf(),
          sentenceAdjustments = listOf(),
        ),
      )
      // NOMIS has one and missing from DPS
      sentencingAdjustmentsApi.stubAdjustmentsGetWithError(
        offenderNo = "A0004TZ",
        status = 404,
      )
      nomisApi.stubGetSentencingAdjustments(
        bookingId = 4,
        sentencingAdjustmentsResponse = SentencingAdjustmentsResponse(
          keyDateAdjustments = listOf(
            keyDateAdjustment(adjustmentType = KeyDateAdjustments.UAL),
          ),
          sentenceAdjustments = listOf(),
        ),
      )
    }

    @Test
    fun `will call DPS for each offenderNo`() = runTest {
      service.generateReconciliationReport(allPrisoners = false)
      sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/adjustments?person=A0001TZ")))
      sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/adjustments?person=A0002TZ")))
      sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/adjustments?person=A0003TZ")))
      sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/adjustments?person=A0004TZ")))
    }

    @Test
    fun `will call NOMIS for each bookingId`() = runTest {
      service.generateReconciliationReport(allPrisoners = false)
      nomisApi.verify(getRequestedFor(urlEqualTo("/prisoners/booking-id/1/sentencing-adjustments?active-only=true")))
      nomisApi.verify(getRequestedFor(urlEqualTo("/prisoners/booking-id/2/sentencing-adjustments?active-only=true")))
      nomisApi.verify(getRequestedFor(urlEqualTo("/prisoners/booking-id/3/sentencing-adjustments?active-only=true")))
      nomisApi.verify(getRequestedFor(urlEqualTo("/prisoners/booking-id/4/sentencing-adjustments?active-only=true")))
    }

    @Test
    fun `will return list of only the mismatches`() = runTest {
      val mismatches = service.generateReconciliationReport(allPrisoners = false)

      assertThat(mismatches).hasSize(2)
      assertThat(mismatches[0].prisonerId.offenderNo).isEqualTo("A0001TZ")
      assertThat(mismatches[1].prisonerId.offenderNo).isEqualTo("A0004TZ")
    }
  }
}

internal fun adjustment(
  adjustmentType: AdjustmentType,
  fromDate: LocalDate = LocalDate.now(),
  effectiveDays: Int = 5,
  bookingId: Long = ADJUSTMENT_BOOKING_ID,
) = AdjustmentDto(
  id = UUID.fromString("0102ab0f-69b0-4292-84d9-bc5fd9f46e66"),
  bookingId = bookingId,
  person = OFFENDER_NO,
  adjustmentType = adjustmentType,
  fromDate = fromDate,
  toDate = fromDate.plusDays(effectiveDays.toLong()),
  effectiveDays = effectiveDays,
  days = effectiveDays,
  status = AdjustmentDto.Status.ACTIVE,
  lastUpdatedBy = "NOMIS",
  lastUpdatedDate = LocalDateTime.now(),
)

internal fun keyDateAdjustment(
  adjustmentType: KeyDateAdjustments,
  fromDate: LocalDate = LocalDate.now(),
  effectiveDays: Int = 5,
) = KeyDateAdjustmentResponse(
  id = 123456L,
  offenderNo = OFFENDER_NO,
  bookingId = ADJUSTMENT_BOOKING_ID,
  adjustmentType = adjustmentType.value,
  adjustmentDate = fromDate,
  adjustmentFromDate = fromDate,
  adjustmentToDate = fromDate.plusDays(effectiveDays.toLong()),
  adjustmentDays = effectiveDays.toLong(),
  active = true,
  comment = null,
  hasBeenReleased = false,
  prisonId = "MDI",
  bookingSequence = 1,
)

internal fun sentenceAdjustment(
  adjustmentType: SentenceAdjustments,
  fromDate: LocalDate = LocalDate.now(),
  effectiveDays: Int = 5,
) = SentenceAdjustmentResponse(
  id = 123456L,
  sentenceSequence = 1,
  offenderNo = OFFENDER_NO,
  bookingId = ADJUSTMENT_BOOKING_ID,
  adjustmentType = adjustmentType.value,
  adjustmentDate = fromDate,
  adjustmentFromDate = fromDate,
  adjustmentToDate = fromDate.plusDays(effectiveDays.toLong()),
  adjustmentDays = effectiveDays.toLong(),
  active = true,
  hiddenFromUsers = false,
  comment = null,
  hasBeenReleased = false,
  prisonId = "MDI",
  bookingSequence = 1,
)

const val ADJUSTMENT_BOOKING_ID = 123456L
const val OFFENDER_NO = "A1234AA"

sealed class SentenceAdjustments(val value: SentencingAdjustmentType) {
  data object RSR : SentenceAdjustments(SentencingAdjustmentType("RSR", "Recall Sentence Remand"))
  data object UR : SentenceAdjustments(SentencingAdjustmentType("UR", "Unused Remand"))
  data object S240A : SentenceAdjustments(SentencingAdjustmentType("S240A", "Sec 240A Tagged Bail Credit - Remand"))
  data object RST : SentenceAdjustments(SentencingAdjustmentType("RST", "Recall Sentence Tagged Bail"))
  data object RX : SentenceAdjustments(SentencingAdjustmentType("RX", "Remand"))
}

sealed class KeyDateAdjustments(val value: SentencingAdjustmentType) {
  data object LAL : KeyDateAdjustments(SentencingAdjustmentType("LAL", "Lawfully at Large"))
  data object UAL : KeyDateAdjustments(SentencingAdjustmentType("UAL", "Unlawfully at Large"))
  data object RADA : KeyDateAdjustments(SentencingAdjustmentType("RADA", "Restoration of Additional Days Awarded"))
  data object ADA : KeyDateAdjustments(SentencingAdjustmentType("ADA", "Additional Days Awarded"))
  data object SREM : KeyDateAdjustments(SentencingAdjustmentType("SREM", "Special Remmission"))
}
