package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PageReportedAdjudicationDto
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

  suspend fun getAdjudicationsByBookingId(bookingId: Long, prisonIds: List<String>): List<ReportedAdjudicationDto> {
    // TODO - switch to simplified non-paged version when available
    return adjudicationsApiWebClient.get()
      .uri("/reported-adjudications/booking/{bookingId}?agency={agency}&status=ACCEPTED,REJECTED,AWAITING_REVIEW,RETURNED,UNSCHEDULED,SCHEDULED,REFER_POLICE,REFER_INAD,REFER_GOV,PROSECUTION,DISMISSED,NOT_PROCEED,ADJOURNED,CHARGE_PROVED,QUASHED,CORRUPTED&page={page}&size={size}", bookingId, prisonIds.joinToString(), 0, 1000)
      .retrieve()
      .awaitBody<PageReportedAdjudicationDto>().content!!
  }
}
