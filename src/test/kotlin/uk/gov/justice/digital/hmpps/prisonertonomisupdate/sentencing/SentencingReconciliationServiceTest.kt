@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.KeyDateAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.SentenceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.SentencingAdjustmentType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto.AdjustmentType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ActivePrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringAPIServiceTest
@Import(
  SentencingReconciliationService::class,
  NomisApiService::class,
  SentencingAdjustmentsApiService::class,
  SentencingConfiguration::class,
)
internal class SentencingReconciliationServiceTest {
  @MockBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var service: SentencingReconciliationService

  @Nested
  inner class WhenBothSystemsHaveNoAdjustments {
    @BeforeEach
    fun beforeEach() {
      sentencingAdjustmentsApi.stubAdjustmentsGet(emptyList())
      NomisApiExtension.nomisApi.stubGetSentencingAdjustments(
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
        service.checkBookingIncentiveMatch(
          ActivePrisonerId(
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
      NomisApiExtension.nomisApi.stubGetSentencingAdjustments(
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
        service.checkBookingIncentiveMatch(
          ActivePrisonerId(
            bookingId = bookingId,
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
      NomisApiExtension.nomisApi.stubGetSentencingAdjustments(
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
        service.checkBookingIncentiveMatch(
          ActivePrisonerId(
            bookingId = bookingId,
            offenderNo = "offenderNo",
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
      NomisApiExtension.nomisApi.stubGetSentencingAdjustments(
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
        service.checkBookingIncentiveMatch(
          ActivePrisonerId(
            bookingId = bookingId,
            offenderNo = "offenderNo",
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
      NomisApiExtension.nomisApi.stubGetSentencingAdjustments(
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
        service.checkBookingIncentiveMatch(
          ActivePrisonerId(
            bookingId = bookingId,
            offenderNo = "offenderNo",
          ),
        ),
      ).isNotNull()
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
      NomisApiExtension.nomisApi.stubGetSentencingAdjustments(
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
        service.checkBookingIncentiveMatch(
          ActivePrisonerId(
            bookingId = bookingId,
            offenderNo = "offenderNo",
          ),
        ),
      ).isNotNull()
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
      NomisApiExtension.nomisApi.stubGetSentencingAdjustments(
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
        service.checkBookingIncentiveMatch(
          ActivePrisonerId(
            bookingId = bookingId,
            offenderNo = "offenderNo",
          ),
        ),
      ).isNotNull()
    }
  }

  private fun adjustment(
    adjustmentType: AdjustmentType,
    fromDate: LocalDate = LocalDate.now(),
    effectiveDays: Int = 5,
  ) = AdjustmentDto(
    id = UUID.fromString("0102ab0f-69b0-4292-84d9-bc5fd9f46e66"),
    bookingId = bookingId,
    person = offenderNo,
    adjustmentType = adjustmentType,
    fromDate = fromDate,
    toDate = fromDate.plusDays(effectiveDays.toLong()),
    effectiveDays = effectiveDays,
    daysBetween = effectiveDays,
    status = AdjustmentDto.Status.ACTIVE,
    lastUpdatedBy = "NOMIS",
    lastUpdatedDate = LocalDateTime.now(),
  )
}

private fun keyDateAdjustment(
  adjustmentType: KeyDateAdjustments,
  fromDate: LocalDate = LocalDate.now(),
  effectiveDays: Int = 5,
) = KeyDateAdjustmentResponse(
  id = 123456L,
  offenderNo = offenderNo,
  bookingId = bookingId,
  adjustmentType = adjustmentType.value,
  adjustmentDate = fromDate,
  adjustmentFromDate = fromDate,
  adjustmentToDate = fromDate.plusDays(effectiveDays.toLong()),
  adjustmentDays = effectiveDays.toLong(),
  active = true,
  comment = null,
)

private fun sentenceAdjustment(
  adjustmentType: SentenceAdjustments,
  fromDate: LocalDate = LocalDate.now(),
  effectiveDays: Int = 5,
) = SentenceAdjustmentResponse(
  id = 123456L,
  sentenceSequence = 1,
  offenderNo = offenderNo,
  bookingId = bookingId,
  adjustmentType = adjustmentType.value,
  adjustmentDate = fromDate,
  adjustmentFromDate = fromDate,
  adjustmentToDate = fromDate.plusDays(effectiveDays.toLong()),
  adjustmentDays = effectiveDays.toLong(),
  active = true,
  hiddenFromUsers = false,
  comment = null,
)

private const val bookingId = 123456L
private const val offenderNo = "A1234AA"

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
