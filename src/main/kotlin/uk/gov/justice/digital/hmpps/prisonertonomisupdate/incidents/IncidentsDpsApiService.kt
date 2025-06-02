package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails

@Service
class IncidentsDpsApiService(@Qualifier("incidentsApiWebClient") private val webClient: WebClient) {
  suspend fun getIncident(incidentId: String): ReportWithDetails = webClient.get()
    .uri("/incident-reports/{incidentId}/with-details", incidentId)
    .retrieve()
    .awaitBody()
}
