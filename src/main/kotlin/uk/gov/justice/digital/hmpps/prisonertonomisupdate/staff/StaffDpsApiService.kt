package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.api.ReconciliationResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse

@Service
class StaffDpsApiService(
  @Qualifier("staffApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", this::class.java.simpleName),
  )

  val api = ReconciliationResourceApi(webClient)

  suspend fun getStaffOrNull(nomisStaffId: Long): PrisonUserReconciliationResponse? = api.getPrisonUserForReconciliation(legacyStaffId = nomisStaffId)
    .awaitBodyOrNullForNotFound(retrySpec = backoffSpec)

  /*
  // TODO Check if needed for reconciliation
  suspend fun getStaffIds(pageNumber: Int = 0, pageSize: Int = 1): PagedModelDpsStaffId = webClient.get()
    .uri {
      it.path("/prison-users/staffIds")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)
   */
}
