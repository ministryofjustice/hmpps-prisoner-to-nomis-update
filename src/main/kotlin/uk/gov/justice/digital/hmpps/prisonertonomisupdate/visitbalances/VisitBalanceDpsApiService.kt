package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.api.BalanceControllerApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.api.NomisControllerApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.VisitAllocationPrisonerAdjustmentResponseDto

@Service
class VisitBalanceDpsApiService(
  visitBalanceApiWebClient: WebClient,
) {
  private val balanceApi = BalanceControllerApi(visitBalanceApiWebClient)
  private val adjustmentApi = NomisControllerApi(visitBalanceApiWebClient)

  suspend fun getVisitBalance(prisonNumber: String): PrisonerBalanceDto? = balanceApi
    .prepare(balanceApi.getPrisonerBalanceRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getVisitBalanceAdjustment(
    prisonNumber: String,
    visitBalanceAdjustmentId: String,
  ): VisitAllocationPrisonerAdjustmentResponseDto = adjustmentApi
    .prepare(adjustmentApi.getPrisonerAdjustmentRequestConfig(prisonNumber, visitBalanceAdjustmentId))
    .retrieve()
    .awaitBody()
}
