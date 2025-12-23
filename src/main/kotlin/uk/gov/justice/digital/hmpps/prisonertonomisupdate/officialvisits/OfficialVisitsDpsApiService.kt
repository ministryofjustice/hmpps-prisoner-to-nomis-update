package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.api.ReconciliationApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit

@Service
class OfficialVisitsDpsApiService(
  @Qualifier("officialVisitsApiWebClient") private val webClient: WebClient,
) {
  private val api = ReconciliationApi(webClient)

  // TODO - once DPS fix swagger
  suspend fun getOfficialVisitIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
  ): PagedModelSyncOfficialVisitIdResponse = api.prepare(
    api.getAllOfficialVisitIdsRequestConfig(
      currentTerm = false,
      page = pageNumber,
      size = pageSize,
    ),
  ).retrieve().awaitBody()

  suspend fun getOfficialVisitOrNull(visitId: Long): SyncOfficialVisit? = api.prepare(api.getOfficialVisitByIdRequestConfig(visitId)).retrieve().awaitBodyOrNullForNotFound()
}

// TODO - once DPS fix swagger
data class SyncOfficialVisitId(
  val officialVisitId: Long,
)

// TODO - once DPS fix swagger
data class PagedModelSyncOfficialVisitIdResponse(

  @get:JsonProperty("content")
  val content: List<SyncOfficialVisitId>? = null,

  @get:JsonProperty("page")
  val page: PageMetadata? = null,

)
