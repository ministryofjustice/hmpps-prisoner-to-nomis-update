package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitBalanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitBalanceResponse

@Service
class VisitBalanceNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getVisitBalance(prisonNumber: String): VisitBalanceResponse? = webClient.get()
    .uri("/prisoners/{prisonNumber}/visit-balance", prisonNumber)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createVisitBalanceAdjustment(prisonNumber: String, visitBalanceAdjustment: CreateVisitBalanceAdjustmentRequest): CreateVisitBalanceAdjustmentResponse = webClient.post()
    .uri("/prisoners/{prisonNumber}/visit-balance-adjustments", prisonNumber)
    .bodyValue(visitBalanceAdjustment)
    .retrieve()
    .awaitBody()

  suspend fun updateVisitBalance(prisonNumber: String, visitBalance: UpdateVisitBalanceRequest) {
    webClient.put()
      .uri("/prisoners/{prisonNumber}/visit-balance", prisonNumber)
      .bodyValue(visitBalance)
      .retrieve()
      .awaitBodilessEntity()
  }
}
