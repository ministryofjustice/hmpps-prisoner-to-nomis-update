package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.Charge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCase

@Service
class CourtSentencingApiService(private val courtSentencingApiWebClient: WebClient) {

  suspend fun getCourtCase(id: String): CourtCase {
    return courtSentencingApiWebClient.get()
      .uri("/court-case/{id}", id)
      .retrieve()
      .awaitBody()
  }

  suspend fun getCourtAppearance(id: String): CourtAppearance {
    return courtSentencingApiWebClient.get()
      .uri("/court-appearance/{id}/lifetime", id)
      .retrieve()
      .awaitBody()
  }

  suspend fun getCourtCharge(id: String): Charge {
    return courtSentencingApiWebClient.get()
      .uri("/charge/{id}/lifetime", id)
      .retrieve()
      .awaitBody()
  }
}
