package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCase

@Service
class CourtSentencingApiService(private val courtSentencingApiWebClient: WebClient) {

  suspend fun getCourtCase(id: String): CourtCase {
    return courtSentencingApiWebClient.get()
      .uri("/TBC", id)
      .retrieve()
      .awaitBody()
  }
}
