package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonPersonReconciliationResponse

@Service("prisonPersonNomisApiService")
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getReconciliation(offenderNo: String): PrisonPersonReconciliationResponse? =
    webClient.get()
      .uri("/prisoners/{offenderNo}/prison-person/reconciliation", offenderNo)
      .retrieve()
      .awaitBodyOrNullForNotFound<PrisonPersonReconciliationResponse>()
}
