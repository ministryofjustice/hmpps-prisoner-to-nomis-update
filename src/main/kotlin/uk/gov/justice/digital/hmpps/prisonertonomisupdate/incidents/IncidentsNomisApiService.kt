package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class IncidentsNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "IncidentsNomisApiService"),
  )

  suspend fun createIncident(nomisId: Long, request: CreateIncidentRequest) {
    // TODO: add implementation
  }

  suspend fun updateIncident(nomisId: Long, request: CreateIncidentRequest) {
    // TODO: add implementation
  }

  suspend fun deleteIncident(nomisId: Long) {
    // TODO: add implementation
  }
}

data class CreateIncidentRequest(val title: String)
