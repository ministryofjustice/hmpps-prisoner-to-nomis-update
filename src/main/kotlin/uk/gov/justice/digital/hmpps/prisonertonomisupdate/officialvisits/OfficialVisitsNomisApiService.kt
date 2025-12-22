package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.OfficialVisitsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagedModelVisitIdResponse

@Service
class OfficialVisitsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = OfficialVisitsResourceApi(webClient)

  suspend fun getOfficialVisitIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
  ): PagedModelVisitIdResponse = api.getOfficialVisitIds(
    page = pageNumber,
    size = pageSize,
    prisonIds = emptyList(),
  ).awaitSingle()
}
