package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.api.AdjustmentControllerApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.api.LegacyControllerApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.LegacyAdjustment
import java.util.UUID

@Service
class SentencingAdjustmentsApiService(private val sentenceAdjustmentsApiWebClient: WebClient) {
  private val legacyControllerApi: LegacyControllerApi = LegacyControllerApi(sentenceAdjustmentsApiWebClient)
  private val adjustmentControllerApi: AdjustmentControllerApi = AdjustmentControllerApi(sentenceAdjustmentsApiWebClient)

  suspend fun getAdjustment(adjustmentId: String): LegacyAdjustment = legacyControllerApi
    .get(UUID.fromString(adjustmentId)).awaitSingle()

  suspend fun getAdjustmentOrNull(adjustmentId: String): LegacyAdjustment? = legacyControllerApi.prepare(
    legacyControllerApi.getRequestConfig(UUID.fromString(adjustmentId)),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getAdjustments(offenderNo: String): List<AdjustmentDto>? = adjustmentControllerApi.prepare(
    adjustmentControllerApi.findByPersonRequestConfig(person = offenderNo),
  )
    .retrieve()
    .awaitBodyOrNullForStatus(NOT_FOUND, HttpStatus.UNPROCESSABLE_CONTENT)
}
