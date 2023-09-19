package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDate

@Service
class SentencingAdjustmentsApiService(private val sentenceAdjustmentsApiWebClient: WebClient) {
  companion object {
    const val LEGACY_CONTENT_TYPE = "application/vnd.nomis-offence+json"
  }

  suspend fun getAdjustment(adjustmentId: String): AdjustmentDetails {
    return sentenceAdjustmentsApiWebClient.get()
      .uri("/legacy/adjustments/$adjustmentId")
      .header("Content-Type", LEGACY_CONTENT_TYPE)
      .retrieve()
      .awaitBody()
  }
}

data class AdjustmentDetails(
  val adjustmentDate: LocalDate?, // TODO remove optional when implemented in adjustments api
  val adjustmentFromDate: LocalDate?,
  val adjustmentDays: Long,
  val bookingId: Long,
  val sentenceSequence: Long?,
  val adjustmentType: String,
  val comment: String?,
  val active: Boolean,
)
