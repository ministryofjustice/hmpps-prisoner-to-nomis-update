package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate

@Service
class SentencingAdjustmentsApiService(private val sentenceAdjustmentsApiWebClient: WebClient) {
  suspend fun getAdjustment(adjustmentId: String): AdjustmentDetails {
    return sentenceAdjustmentsApiWebClient.get()
      .uri("/adjustments/$adjustmentId")
      .retrieve()
      .bodyToMono(AdjustmentDetails::class.java)
      .awaitSingle()
  }
}

data class AdjustmentDetails(
  val adjustmentId: String,
  val adjustmentDate: LocalDate,
  val adjustmentStartPeriod: LocalDate?,
  val adjustmentDays: Long,
  val bookingId: Long,
  val sentenceSequence: Long?,
  val adjustmentType: String,
  val comment: String?,
)
