package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCase

@Service
class CourtSentencingApiService(private val courtSentencingApiWebClient: WebClient) {

  suspend fun getCourtCase(id: String): LegacyCourtCase {
    return courtSentencingApiWebClient.get()
      .uri("/legacy/court-case/{id}", id)
      .retrieve()
      .awaitBody()
  }

  suspend fun getCourtCaseForReconciliation(id: String): CourtCase {
    return courtSentencingApiWebClient.get()
      .uri("/court-case/{id}", id)
      .retrieve()
      .awaitBody()
  }

  suspend fun getCourtAppearance(id: String): LegacyCourtAppearance {
    return courtSentencingApiWebClient.get()
      .uri("/legacy/court-appearance/{id}", id)
      .retrieve()
      .awaitBody()
  }

  suspend fun getCourtCharge(id: String): LegacyCharge {
    return courtSentencingApiWebClient.get()
      .uri("/legacy/charge/{id}", id)
      .retrieve()
      .awaitBody()
  }
}
