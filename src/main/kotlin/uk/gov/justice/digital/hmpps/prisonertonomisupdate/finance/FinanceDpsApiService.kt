package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.api.NOMISSyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.api.PrisonerTrustAccountsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerSubAccountDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.UUID

@Service
class FinanceDpsApiService(
  @Qualifier("financeApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "FinanceApiService"),
  )

  private val syncApi = NOMISSyncApi(webClient)
  private val prisonerApi = PrisonerTrustAccountsApi(webClient)

  suspend fun getPrisonAccounts(prisonId: String): GeneralLedgerBalanceDetailsList = syncApi
    .listGeneralLedgerBalances(prisonId)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonerAccounts(prisonNumber: String): PrisonerEstablishmentBalanceDetailsList = syncApi
    .listPrisonerBalancesByEstablishment(prisonNumber)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonTransaction(id: UUID): SyncGeneralLedgerTransactionResponse = syncApi
    .getGeneralLedgerTransactionById(id)
    .awaitSingle()

  suspend fun getOffenderTransaction(id: UUID): SyncOffenderTransactionResponse = syncApi
    .getOffenderTransactionById(id)
    .awaitSingle()

  // TODO check if this is still needed
  suspend fun getPrisonerSubAccountDetails(prisonerNo: String, account: Int): PrisonerSubAccountDetails = prisonerApi
    .getPrisonerSubAccountDetails(prisonerNo, account)
    .retryWhen(backoffSpec)
    .awaitSingle()
}
