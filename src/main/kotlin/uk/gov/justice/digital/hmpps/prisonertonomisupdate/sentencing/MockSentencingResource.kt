package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import io.swagger.v3.oas.annotations.Operation
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class MockSentencingResource(
  private val mockSentenceAdjustmentData: MockSentenceAdjustmentData,
  private val mockKeyDateAdjustmentData: MockKeyDateAdjustmentData,
) {
  @GetMapping("/adjustments/{adjustmentId}")
  @Operation(hidden = true)
  fun getAdjustment(
    @PathVariable adjustmentId: String,
  ): AdjustmentDetails = if (adjustmentId.endsWith("S")) AdjustmentDetails(
    adjustmentId = adjustmentId,
    adjustmentDate = LocalDate.now(),
    adjustmentStartPeriod = LocalDate.now().minusMonths(1),
    adjustmentDays = 15,
    bookingId = mockSentenceAdjustmentData.bookingId,
    sentenceSequence = mockSentenceAdjustmentData.sentenceSequence,
    adjustmentType = mockSentenceAdjustmentData.adjustmentType,
    comment = "Created using mock data",
    creatingSystem = CreatingSystem.SENTENCE_ADJUSTMENTS,
  ) else AdjustmentDetails(
    adjustmentId = adjustmentId,
    adjustmentDate = LocalDate.now(),
    adjustmentStartPeriod = LocalDate.now().minusMonths(1),
    adjustmentDays = 15,
    bookingId = mockKeyDateAdjustmentData.bookingId,
    sentenceSequence = null,
    adjustmentType = mockKeyDateAdjustmentData.adjustmentType,
    comment = "Created using mock data",
    creatingSystem = CreatingSystem.SENTENCE_ADJUSTMENTS,
  )
}

@Configuration
@ConfigurationProperties(prefix = "mock.adjustment.sentence")
data class MockSentenceAdjustmentData(
  var bookingId: Long = 1201725,
  var sentenceSequence: Long = 1,
  var adjustmentType: String = "RX",
  var offenderNo: String = "A5194DY"
)

@Configuration
@ConfigurationProperties(prefix = "mock.adjustment.key.date")
data class MockKeyDateAdjustmentData(
  var bookingId: Long = 1201725,
  var adjustmentType: String = "ADA",
  var offenderNo: String = "A5194DY"
)
