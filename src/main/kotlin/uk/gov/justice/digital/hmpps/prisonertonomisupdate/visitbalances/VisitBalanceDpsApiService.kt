package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visit.balance.model.PrisonerBalanceDto

@Service
class VisitBalanceDpsApiService(
  private val visitBalanceApiWebClient: WebClient,
) {
  suspend fun getVisitBalance(prisonNumber: String): PrisonerBalanceDto? = visitBalanceApiWebClient.get()
    .uri("/visits/allocation/prisoner/{prisonNumber}/balance", prisonNumber)
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
