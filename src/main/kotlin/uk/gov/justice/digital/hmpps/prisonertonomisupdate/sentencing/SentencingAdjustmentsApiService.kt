package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.LegacyAdjustment

@Service
class SentencingAdjustmentsApiService(private val sentenceAdjustmentsApiWebClient: WebClient) {
  companion object {
    const val LEGACY_CONTENT_TYPE = "application/vnd.nomis-offence+json"
  }

  suspend fun getAdjustment(adjustmentId: String): LegacyAdjustment = sentenceAdjustmentsApiWebClient.get()
    .uri("/legacy/adjustments/{adjustmentId}", adjustmentId)
    .header("Content-Type", LEGACY_CONTENT_TYPE)
    .retrieve()
    .awaitBody()

  suspend fun getAdjustmentOrNull(adjustmentId: String): LegacyAdjustment? = sentenceAdjustmentsApiWebClient.get()
    .uri("/legacy/adjustments/{adjustmentId}", adjustmentId)
    .header("Content-Type", LEGACY_CONTENT_TYPE)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getAdjustments(offenderNo: String): List<AdjustmentDto>? = sentenceAdjustmentsApiWebClient.get()
    .uri("/adjustments?person={offenderNo}", offenderNo)
    .retrieve()
    .awaitBodyOrNullForStatus(NOT_FOUND, HttpStatus.UNPROCESSABLE_CONTENT)
}
