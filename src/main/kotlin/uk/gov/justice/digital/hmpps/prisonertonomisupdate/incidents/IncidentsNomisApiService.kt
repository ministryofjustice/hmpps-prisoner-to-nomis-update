package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequest

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
