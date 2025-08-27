package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncOffenderTransactionResponse
import java.util.UUID

@Service
class FinanceDpsApiService(@Qualifier("financeApiWebClient") private val webClient: WebClient) {
  suspend fun getOffenderTransaction(id: UUID): SyncOffenderTransactionResponse = webClient
    .get()
    .uri("/sync/offender-transactions/{id}", id)
    .retrieve()
    .awaitBody()

  suspend fun getGeneralLedgerTransaction(id: UUID): SyncGeneralLedgerTransactionResponse = webClient
    .get()
    .uri("/sync/general-ledger-transactions/{id}", id)
    .retrieve()
    .awaitBody()
}
