package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitBalanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class VisitBalanceNomisApiService(
  @Qualifier("nomisApiWebClient") webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", this::class.java.simpleName),
  )
  private val api = VisitBalanceResourceApi(webClient)

  suspend fun getVisitBalance(prisonNumber: String): VisitBalanceResponse? = api
    .prepare(api.getVisitBalanceForPrisonerRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullForNotFound(retrySpec = backoffSpec)

  suspend fun createVisitBalanceAdjustment(prisonNumber: String, visitBalanceAdjustment: CreateVisitBalanceAdjustmentRequest): CreateVisitBalanceAdjustmentResponse = api
    .prepare(api.createVisitBalanceAdjustmentRequestConfig(prisonNumber, visitBalanceAdjustment))
    .retrieve()
    .awaitBody()

  suspend fun updateVisitBalance(prisonNumber: String, visitBalance: UpdateVisitBalanceRequest) {
    api.prepare(api.updateVisitBalanceRequestConfig(prisonNumber, visitBalance))
      .retrieve()
      .awaitBodilessEntity()
  }
}
