package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import org.openapitools.client.infrastructure.RequestConfig
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.api.AdjustmentControllerApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.api.LegacyControllerApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.LegacyAdjustment
import java.util.UUID

@Service
class SentencingAdjustmentsApiService(sentenceAdjustmentsApiWebClient: WebClient) {
  private val legacyControllerApi: LegacyControllerApi = LegacyControllerApi(sentenceAdjustmentsApiWebClient)
  private val adjustmentControllerApi: AdjustmentControllerApi = AdjustmentControllerApi(sentenceAdjustmentsApiWebClient)

  suspend fun getAdjustment(adjustmentId: String): LegacyAdjustment = legacyControllerApi.prepare(
    legacyControllerApi.getRequestConfig(UUID.fromString(adjustmentId)).apply {
      setLegacyContentTypeHeader()
    },
  )
    .retrieve()
    .awaitBody()

  suspend fun getAdjustmentOrNull(adjustmentId: String): LegacyAdjustment? = legacyControllerApi.prepare(
    legacyControllerApi.getRequestConfig(UUID.fromString(adjustmentId)).apply {
      setLegacyContentTypeHeader()
    },
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getAdjustments(offenderNo: String): List<AdjustmentDto>? = adjustmentControllerApi.prepare(
    adjustmentControllerApi.findByPersonRequestConfig(person = offenderNo).apply {
      setLegacyContentTypeHeader()
    },
  )
    .retrieve()
    .awaitBodyOrNullForStatus(NOT_FOUND, HttpStatus.UNPROCESSABLE_CONTENT)

  private fun RequestConfig<Unit>.setLegacyContentTypeHeader() {
    headers["Content-Type"] = "application/vnd.nomis-offence+json"
  }
}
