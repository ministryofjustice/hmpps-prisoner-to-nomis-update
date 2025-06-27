package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentAgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentsReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference

@Service
class IncidentsNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
) {
  suspend fun upsertIncident(nomisId: Long, request: UpsertIncidentRequest) {
    webClient.put()
      .uri("/incidents/{incidentId}", nomisId)
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteIncident(nomisId: Long) {
    webClient.delete()
      .uri("/incidents/{incidentId}", nomisId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getAgenciesWithIncidents(): List<IncidentAgencyId> = webClient.get()
    .uri("/incidents/reconciliation/agencies")
    .retrieve()
    .awaitBody()

  suspend fun getIncident(incidentId: Long): IncidentResponse = webClient.get()
    .uri("/incidents/{incidentId}", incidentId)
    .retrieve()
    .bodyToMono(IncidentResponse::class.java)
    .awaitSingle()

  suspend fun getAgencyIncidentCounts(agencyId: String): IncidentsReconciliationResponse = webClient.get()
    .uri("/incidents/reconciliation/agency/{agencyId}/counts", agencyId)
    .retrieve()
    .awaitBody()

  suspend fun getOpenIncidentIds(
    agencyId: String,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<IncidentIdResponse> = webClient.get()
    .uri {
      it.path("/incidents/reconciliation/agency/{agencyId}/ids")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build(agencyId)
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<IncidentIdResponse>>())
    .awaitSingle()
}

// data class CreateIncidentQuestionRequest(
//  val code: String,
//  val additionalInformation: String?,
//  val question: String,
//  val responses: List<CreateIncidentResponseRequest>,
// )
//
// data class CreateIncidentResponseRequest(
//  val response: String,
//  val responseDate: LocalDate?,
//  val additionalInformation: String?,
//  val recordedAt: LocalDateTime,
//  val recordedBy: String,
// )
