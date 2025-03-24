package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerVisitBalanceResponse

@Service
class VisitBalanceNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun getVisitBalance(prisonNumber: String): PrisonerVisitBalanceResponse = webClient.get().uri("/prisoners/{prisonNumber}/visit-orders/balance", prisonNumber)
    .retrieve()
    .awaitBody()
}
