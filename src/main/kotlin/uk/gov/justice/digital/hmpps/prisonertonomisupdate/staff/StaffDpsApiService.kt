package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class StaffDpsApiService(
  @Qualifier("staffApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", this::class.java.simpleName),
  )

  suspend fun getStaffOrNull(staffId: Long): DpsStaffDetails? = webClient.get()
    .uri("/prison-users/{staffId}", staffId)
    .retrieve()
    .awaitBodyOrNullForNotFound(retrySpec = backoffSpec)

  // May need for reconciliation
  suspend fun getStaffIds(pageNumber: Int = 0, pageSize: Int = 1): PagedModelStaffId = webClient.get()
    .uri {
      it.path("/prison-users/staffIds")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)
}
