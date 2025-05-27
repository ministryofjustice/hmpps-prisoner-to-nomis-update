package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ConvertToRecallRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtAppearanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtCaseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceTermRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCourtAppearanceResponse

@Service
class CourtSentencingNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun createCourtCase(offenderNo: String, request: CreateCourtCaseRequest): CreateCourtCaseResponse = webClient.post()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun deleteCourtCase(offenderNo: String, nomisCourtCaseId: Long): ResponseEntity<Void> = webClient.delete()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases/{nomisCourtCaseId}", offenderNo, nomisCourtCaseId)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun deleteCourtAppearance(offenderNo: String, nomisCourtCaseId: Long, nomisEventId: Long): ResponseEntity<Void> = webClient.delete()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases/{nomisCourtCaseId}/court-appearances/{nomisEventId}", offenderNo, nomisCourtCaseId, nomisEventId)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun createCourtAppearance(offenderNo: String, nomisCourtCaseId: Long, request: CourtAppearanceRequest): CreateCourtAppearanceResponse = webClient.post()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases/{courtCaseId}/court-appearances", offenderNo, nomisCourtCaseId)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createCourtCharge(offenderNo: String, nomisCourtCaseId: Long, request: OffenderChargeRequest): OffenderChargeIdResponse = webClient.post()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases/{courtCaseId}/charges", offenderNo, nomisCourtCaseId)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateCourtCharge(chargeId: Long, offenderNo: String, nomisCourtCaseId: Long, nomisCourtAppearanceId: Long, request: OffenderChargeRequest): ResponseEntity<Void> = webClient.put()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases/{courtCaseId}/court-appearances/{courtEventId}/charges/{chargeId}", offenderNo, nomisCourtCaseId, nomisCourtAppearanceId, chargeId)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun updateCourtAppearance(offenderNo: String, nomisCourtCaseId: Long, nomisCourtAppearanceId: Long, request: CourtAppearanceRequest): UpdateCourtAppearanceResponse = webClient.put()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases/{nomisCourtCaseId}/court-appearances/{nomisCourtAppearanceId}", offenderNo, nomisCourtCaseId, nomisCourtAppearanceId)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun refreshCaseReferences(offenderNo: String, nomisCourtCaseId: Long, request: CaseIdentifierRequest): ResponseEntity<Void> = webClient.post()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases/{caseId}/case-identifiers", offenderNo, nomisCourtCaseId)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getCourtCaseForMigration(courtCaseId: Long): CourtCaseResponse = webClient.get()
    .uri("/court-cases/{courtCaseId}", courtCaseId)
    .retrieve()
    .awaitBody()

  suspend fun getCourtCasesByOffender(offenderNo: String): List<CourtCaseResponse> = webClient.get()
    .uri("/prisoners/{offenderNo}/sentencing/court-cases", offenderNo)
    .retrieve()
    .awaitBody()

  suspend fun createSentence(
    offenderNo: String,
    caseId: Long,
    request: CreateSentenceRequest,
  ): CreateSentenceResponse = webClient.post()
    .uri("/prisoners/{offenderNo}/court-cases/{caseId}/sentences", offenderNo, caseId)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateSentence(
    offenderNo: String,
    sentenceSeq: Int,
    caseId: Long,
    request: CreateSentenceRequest,
  ) = webClient.put()
    .uri("/prisoners/{offenderNo}/court-cases/{caseId}/sentences/{sentenceSeq}", offenderNo, caseId, sentenceSeq)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun deleteSentence(
    offenderNo: String,
    sentenceSeq: Int,
    caseId: Long,
  ) = webClient.delete()
    .uri("/prisoners/{offenderNo}/court-cases/{caseId}/sentences/{sentenceSeq}", offenderNo, caseId, sentenceSeq)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun createSentenceTerm(
    offenderNo: String,
    caseId: Long,
    sentenceSeq: Int,
    request: SentenceTermRequest,
  ): CreateSentenceTermResponse = webClient.post()
    .uri("/prisoners/{offenderNo}/court-cases/{caseId}/sentences/{sentenceSeq}/sentence-terms", offenderNo, caseId, sentenceSeq)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateSentenceTerm(
    offenderNo: String,
    sentenceSeq: Int,
    termSeq: Int,
    caseId: Long,
    request: SentenceTermRequest,
  ) = webClient.put()
    .uri("/prisoners/{offenderNo}/court-cases/{caseId}/sentences/{sentenceSeq}/sentence-terms/{termSequence}", offenderNo, caseId, sentenceSeq, termSeq)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun deleteSentenceTerm(
    offenderNo: String,
    sentenceSeq: Int,
    termSeq: Int,
    caseId: Long,
  ) = webClient.delete()
    .uri("/prisoners/{offenderNo}/court-cases/{caseId}/sentences/{sentenceSeq}/sentence-terms/{termSequence}", offenderNo, caseId, sentenceSeq, termSeq)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun recallSentences(
    offenderNo: String,
    request: ConvertToRecallRequest,
  ) = webClient.post()
    .uri("/prisoners/{offenderNo}/sentences/recall", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()
}
