package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.DPSService
import java.time.LocalDate

@Service
class SentencingAdjustmentsApiService(private val sentenceAdjustmentsApiWebClient: WebClient) : DPSService<String, AdjustmentDetails>() {
  suspend fun getAdjustment(adjustmentId: String): AdjustmentDetails {
    return sentenceAdjustmentsApiWebClient.get()
      .uri("/adjustments/$adjustmentId")
      .retrieve()
      .bodyToMono(AdjustmentDetails::class.java)
      .awaitSingle()
  }

  override suspend fun getEntityFromDPS(id: String): AdjustmentDetails = getAdjustment(id)
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
  val creatingSystem: CreatingSystem,
)

enum class CreatingSystem {
  NOMIS,
  SENTENCE_ADJUSTMENTS,
}
