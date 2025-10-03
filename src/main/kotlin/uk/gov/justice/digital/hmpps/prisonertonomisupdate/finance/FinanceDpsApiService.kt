package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.api.NOMISSyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.api.PrisonAccountsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.api.PrisonerTrustAccountsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.AccountDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonAccountDetails
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
  private val prisonApi = PrisonAccountsApi(webClient)

  suspend fun getOffenderTransaction(id: UUID): SyncOffenderTransactionResponse = syncApi
    .getOffenderTransactionById(id)
    .awaitSingle()

  suspend fun getGeneralLedgerTransaction(id: UUID): SyncGeneralLedgerTransactionResponse = syncApi
    .getGeneralLedgerTransactionById(id)
    .awaitSingle()

  suspend fun listPrisonerAccounts(prisonerNo: String): AccountDetailsList = prisonerApi
    .listPrisonerAccounts(prisonerNo)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonerSubAccountDetails(prisonerNo: String, account: Int): PrisonerSubAccountDetails = prisonerApi
    .getPrisonerSubAccountDetails(prisonerNo, account)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun listPrisonAccounts(prisonId: String): AccountDetailsList = prisonApi
    .listPrisonAccounts(prisonId)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getPrisonAccountDetails(prisonId: String, account: Int): PrisonAccountDetails = prisonApi
    .getPrisonAccountDetails(prisonId, account)
    .retryWhen(backoffSpec)
    .awaitSingle()
}
