package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.SimplePageReportBasic

@Service
class IncidentsDpsApiService(@Qualifier("incidentsApiWebClient") private val webClient: WebClient) {
  companion object {
    val openStatusValues = listOf("AWAITING_REVIEW", "NEEDS_UPDATING", "ON_HOLD", "POST_INCIDENT_UPDATE", "UPDATED")
    val closedStatusValues = listOf("CLOSED", "DUPLICATE", "NOT_REPORTABLE")
  }

  suspend fun getIncident(incidentId: String): ReportWithDetails = webClient.get()
    .uri("/incident-reports/{incidentId}/with-details", incidentId)
    .retrieve()
    .awaitBody()

  suspend fun getIncidentsByAgencyAndStatus(agencyId: String, statusValues: List<String>): SimplePageReportBasic = webClient.get()
    .uri {
      it.path("/incident-reports")
        .queryParam("location", agencyId)
        .queryParam("status", statusValues)
        .queryParam("size", 1)
        .build()
    }
    .retrieve()
    .awaitBody()

  suspend fun getIncidentDetailsByNomisId(nomisIncidentId: Long): ReportWithDetails = webClient.get()
    .uri("/incident-reports/reference/{nomisIncidentId}/with-details", nomisIncidentId)
    .retrieve()
    .awaitBody()

  suspend fun getOpenIncidentsCount(agencyId: String) = getIncidentsByAgencyAndStatus(agencyId, openStatusValues).totalElements

  suspend fun getClosedIncidentsCount(agencyId: String) = getIncidentsByAgencyAndStatus(agencyId, closedStatusValues).totalElements
}
