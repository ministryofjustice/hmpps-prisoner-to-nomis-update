package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.api.ReconciliationApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.PagedModelSyncOfficialVisitId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class OfficialVisitsDpsApiService(
  @Qualifier("officialVisitsApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = ReconciliationApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "OfficialVisitsDpsApiService"),
  )

  suspend fun getOfficialVisitIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
  ): PagedModelSyncOfficialVisitId = api.prepare(
    api.getAllOfficialVisitIdsRequestConfig(
      currentTermOnly = false,
      page = pageNumber,
      size = pageSize,
    ),
  ).retrieve().awaitBodyWithRetry(retrySpec)

  suspend fun getOfficialVisitOrNull(visitId: Long): SyncOfficialVisit? = api.prepare(api.getOfficialVisitByIdRequestConfig(visitId)).retrieve().awaitBodyOrNullForNotFound(retrySpec)
}
