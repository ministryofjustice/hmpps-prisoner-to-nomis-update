package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.api.ReconciliationApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.api.SynchronisationApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.PagedModelSyncOfficialVisitId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncTimeSlotSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncVisitSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@Service
class OfficialVisitsDpsApiService(
  @Qualifier("officialVisitsApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = ReconciliationApi(webClient)
  private val syncApi = SynchronisationApi(webClient)
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

  suspend fun getOfficialVisitOrNull(visitId: Long): SyncOfficialVisit? = api.prepare(api.getOfficialVisitById1RequestConfig(visitId)).retrieve().awaitBodyOrNullForNotFound(retrySpec)
  suspend fun getOfficialVisit(visitId: Long): SyncOfficialVisit = api.getOfficialVisitById1(officialVisitId = visitId).awaitSingle()

  suspend fun getOfficialVisitsForPrisoner(
    offenderNo: String,
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
    currentTermOnly: Boolean = false,
  ): List<SyncOfficialVisit> = api.getAllOfficialVisitForPrisoner(
    prisonerNumber = offenderNo,
    fromDate = fromDate,
    toDate = toDate,
    currentTermOnly = currentTermOnly,
  ).retryWhen(retrySpec).awaitSingle()

  suspend fun getTimeSlotsForPrison(prisonId: String, activeOnly: Boolean = false): SyncTimeSlotSummary = api.summariseTimeSlotsAndVisitSlots(prisonCode = prisonId, activeOnly = activeOnly)
    .retryWhen(retrySpec).awaitSingle()

  suspend fun getTimeSlot(prisonTimeSlotId: Long): SyncTimeSlot = syncApi.syncGetTimeSlotById(prisonTimeSlotId = prisonTimeSlotId)
    .awaitSingle()

  suspend fun getVisitSlot(prisonVisitSlotId: Long): SyncVisitSlot = syncApi.syncGetVisitSlotById(prisonVisitSlotId = prisonVisitSlotId)
    .awaitSingle()
}
