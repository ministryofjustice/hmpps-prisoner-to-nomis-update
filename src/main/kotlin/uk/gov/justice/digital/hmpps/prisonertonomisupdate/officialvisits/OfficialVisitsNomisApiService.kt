package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.OfficialVisitsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagedModelVisitIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitIdsPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate

@Service
class OfficialVisitsNomisApiService(
  @Qualifier("nomisApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = OfficialVisitsResourceApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "OfficialVisitsMappingService"),
  )

  suspend fun getOfficialVisitIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
  ): PagedModelVisitIdResponse = api.getOfficialVisitIds(
    page = pageNumber,
    size = pageSize,
    prisonIds = emptyList(),
  ).retryWhen(retrySpec).awaitSingle()

  suspend fun createOfficialVisit(
    offenderNo: String,
    request: CreateOfficialVisitRequest,
  ): OfficialVisitResponse = api.createOfficialVisit(
    offenderNo = offenderNo,
    createOfficialVisitRequest = request,
  ).awaitSingle()

  suspend fun getOfficialVisit(
    visitId: Long,
  ): OfficialVisitResponse = api.getOfficialVisit(
    visitId = visitId,
  ).retryWhen(retrySpec).awaitSingle()

  suspend fun createOfficialVisitor(
    visitId: Long,
    request: CreateOfficialVisitorRequest,
  ): OfficialVisitor = api.createOfficialVisitor(
    visitId = visitId,
    createOfficialVisitorRequest = request,
  ).awaitSingle()

  suspend fun getOfficialVisitOrNull(
    visitId: Long,
  ): OfficialVisitResponse? = api.prepare(api.getOfficialVisitRequestConfig(visitId)).retrieve().awaitBodyOrNullForNotFound(retrySpec)

  suspend fun getOfficialVisitIdsByLastId(
    lastVisitId: Long = 0,
    pageSize: Long,
  ): VisitIdsPage = api.getOfficialVisitIdsFromIds(
    visitId = lastVisitId,
    size = pageSize.toInt(),
    prisonIds = emptyList(),
  ).retryWhen(retrySpec).awaitSingle()

  suspend fun getOfficialVisitsForPrisoner(
    offenderNo: String,
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
  ): List<OfficialVisitResponse> = api.getOfficialVisitsForPrisoner(
    offenderNo = offenderNo,
    fromDate = fromDate,
    toDate = toDate,
  ).retryWhen(retrySpec).awaitSingle()

  suspend fun deleteOfficialVisitor(
    visitId: Long,
    visitorId: Long,
  ) {
    api.deleteOfficialVisitor(
      visitId = visitId,
      visitorId = visitorId,
    ).awaitSingle()
  }
}
