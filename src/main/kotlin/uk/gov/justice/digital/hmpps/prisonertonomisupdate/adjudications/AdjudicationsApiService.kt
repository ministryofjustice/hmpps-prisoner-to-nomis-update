package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponse

@Service
class AdjudicationsApiService(private val adjudicationsApiWebClient: WebClient) {

  suspend fun getCharge(chargeNumber: String, prisonId: String): ReportedAdjudicationResponse {
    return adjudicationsApiWebClient.get()
      .uri("/reported-adjudications/{chargeNumber}/v2", chargeNumber)
      .header("Active-Caseload", prisonId)
      .retrieve()
      .awaitBody()
  }

  suspend fun getAdjudicationsByBookingId(bookingId: Long): List<ReportedAdjudicationDto> {
    return adjudicationsApiWebClient.get()
      .uri("/reported-adjudications/all-by-booking/{bookingId}", bookingId)
      .retrieve()
      .awaitBody<List<ReportedAdjudicationDto>>()
  }
}
