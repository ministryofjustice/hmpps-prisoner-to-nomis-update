package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.api.IncidentReportsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.api.IncidentReportsApi.StatusGetBasicReports
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.SimplePageReportBasic
import java.util.UUID

@Service
class IncidentsDpsApiService(@Qualifier("incidentsApiWebClient") private val webClient: WebClient) {
  companion object {
    val openStatusValues = listOf(StatusGetBasicReports.AWAITING_REVIEW, StatusGetBasicReports.NEEDS_UPDATING, StatusGetBasicReports.ON_HOLD, StatusGetBasicReports.UPDATED)
    val closedStatusValues = listOf(StatusGetBasicReports.CLOSED, StatusGetBasicReports.DUPLICATE, StatusGetBasicReports.NOT_REPORTABLE, StatusGetBasicReports.REOPENED, StatusGetBasicReports.WAS_CLOSED)
  }
  private val incidentsApi = IncidentReportsApi(webClient)

  suspend fun getIncident(incidentId: String): ReportWithDetails = incidentsApi
    .getReportWithDetailsById(UUID.fromString(incidentId))
    .awaitSingle()

  suspend fun getIncidentsByAgencyAndStatus(agencyId: String, statusValues: List<StatusGetBasicReports>): SimplePageReportBasic = incidentsApi
    .getBasicReports(location = listOf(agencyId), status = statusValues, size = 1)
    .awaitSingle()

  suspend fun getIncidentDetailsByNomisId(nomisIncidentId: Long): ReportWithDetails = incidentsApi
    .getReportWithDetailsByReference(nomisIncidentId.toString())
    .awaitSingle()

  suspend fun getOpenIncidentsCount(agencyId: String) = getIncidentsByAgencyAndStatus(agencyId, openStatusValues).totalElements

  suspend fun getClosedIncidentsCount(agencyId: String) = getIncidentsByAgencyAndStatus(agencyId, closedStatusValues).totalElements
}
