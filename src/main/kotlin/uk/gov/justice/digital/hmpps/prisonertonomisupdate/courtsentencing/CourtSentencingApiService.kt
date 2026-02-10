package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCaseUuids
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyRecall
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacySearchSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacySentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CourtSentencingApiService(private val courtSentencingApiWebClient: WebClient, retryApiService: RetryApiService) {

  suspend fun getCourtCase(id: String): LegacyCourtCase = courtSentencingApiWebClient.get()
    .uri("/legacy/court-case/{id}", id)
    .retrieve()
    .awaitBody()

  suspend fun getCourtCaseOrNull(id: String): LegacyCourtCase? = courtSentencingApiWebClient.get()
    .uri("/legacy/court-case/{id}", id)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getCourtCaseForReconciliation(courtCaseUuid: String): ReconciliationCourtCase = courtSentencingApiWebClient.get()
    .uri("/legacy/court-case/{courtCaseUuid}/reconciliation", courtCaseUuid)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CourtSentencingApiService"),
  )

  suspend fun getCourtAppearance(id: String): LegacyCourtAppearance = courtSentencingApiWebClient.get()
    .uri("/legacy/court-appearance/{id}", id)
    .retrieve()
    .awaitBody()

  suspend fun getCourtCharge(id: String): LegacyCharge = courtSentencingApiWebClient.get()
    .uri("/legacy/charge/{id}", id)
    .retrieve()
    .awaitBody()

  suspend fun getCourtChargeByAppearance(appearanceId: String, chargeId: String): LegacyCharge = courtSentencingApiWebClient.get()
    .uri("/legacy/court-appearance/{appearanceId}/charge/{chargeId}", appearanceId, chargeId)
    .retrieve()
    .awaitBody()

  suspend fun getSentence(id: String): LegacySentence = courtSentencingApiWebClient.get()
    .uri("/legacy/sentence/{id}", id)
    .retrieve()
    .awaitBody()

  suspend fun getPeriodLength(id: String): LegacyPeriodLength = courtSentencingApiWebClient.get()
    .uri("/legacy/period-length/{id}", id)
    .retrieve()
    .awaitBody()

  suspend fun getRecall(id: String): LegacyRecall = courtSentencingApiWebClient.get()
    .uri("/legacy/recall/{id}", id)
    .retrieve()
    .awaitBody()

  suspend fun getSentences(sentences: LegacySearchSentence): List<LegacySentence> = courtSentencingApiWebClient.post()
    .uri("/legacy/sentence/search")
    .bodyValue(sentences)
    .retrieve()
    .awaitBody()

  suspend fun getCourtCaseIdsForOffender(offenderNo: String): LegacyCourtCaseUuids = courtSentencingApiWebClient.get()
    .uri("/legacy/prisoner/{prisonerId}/court-case-uuids", offenderNo)
    .retrieve()
    .awaitBody()
}
