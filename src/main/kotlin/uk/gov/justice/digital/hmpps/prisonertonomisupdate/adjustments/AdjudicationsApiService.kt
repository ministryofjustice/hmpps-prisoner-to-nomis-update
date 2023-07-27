package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjustments

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponseV2

@Service
class AdjudicationsApiService(private val adjudicationsApiWebClient: WebClient) {

  suspend fun getCharge(chargeNumber: String, prisonId: String): ReportedAdjudicationResponseV2 {
    return adjudicationsApiWebClient.get()
      .uri("/reported-adjudications/$chargeNumber/v2")
      .header("Active-Caseload", prisonId)
      .retrieve()
      .awaitBody()
  }
}
