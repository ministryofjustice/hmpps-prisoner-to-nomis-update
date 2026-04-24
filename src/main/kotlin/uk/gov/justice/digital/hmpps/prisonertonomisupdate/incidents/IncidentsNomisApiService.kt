package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.IncidentResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentAgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentsReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class IncidentsNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val nomisIncidentsApi = IncidentResourceApi(webClient)
  private val retrySpecOnLocked = retryApiService.getBackoffSpec().filter { ex ->
    ex is WebClientResponseException && ex.statusCode == HttpStatus.LOCKED
  }

  suspend fun upsertIncident(nomisId: Long, request: UpsertIncidentRequest) {
    nomisIncidentsApi
      .upsertIncident(nomisId, request)
      .awaitBodyWithRetry(
        retrySpecOnLocked.withRetryContext(
          Context.of("api", "nomis-prisoner-api", "url", "/incidents/$nomisId"),
        ),
      )
  }

  suspend fun deleteIncident(nomisId: Long) {
    nomisIncidentsApi
      .deleteIncidentByIncidentId(nomisId)
      .awaitSingle()
  }

  suspend fun getAgenciesWithIncidents(): List<IncidentAgencyId> = nomisIncidentsApi
    .getIncidentAgencies()
    .awaitSingle()

  suspend fun getIncident(incidentId: Long): IncidentResponse = nomisIncidentsApi
    .getIncident(incidentId)
    .awaitSingle()

  suspend fun getAgencyIncidentCounts(agencyId: String): IncidentsReconciliationResponse = nomisIncidentsApi
    .getIncidentCountsForReconciliation(agencyId)
    .awaitSingle()

  suspend fun getOpenIncidentIds(agencyId: String, pageNumber: Long, pageSize: Long): List<IncidentIdResponse> = nomisIncidentsApi.getOpenIncidentIdsForReconciliation(
    agencyId = agencyId,
    page = pageNumber.toInt(),
    size = pageSize.toInt(),
  ).awaitSingle().content ?: listOf()
}
