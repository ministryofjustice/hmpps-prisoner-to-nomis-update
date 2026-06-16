package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.StaffResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagedModelStaffIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffIdsPage

@Service
class StaffNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = StaffResourceApi(webClient)

  suspend fun getStaffDetails(staffId: Long): StaffDetails = api.getStaff(staffId, true)
    .awaitSingle()

  suspend fun getStaffIds(pageNumber: Long = 0, pageSize: Long = 1): PagedModelStaffIdResponse = api.getStaffIds(page = pageNumber.toInt(), size = pageSize.toInt())
    .awaitSingle()

  suspend fun getStaffIdsFromId(lastStaffId: Long = 0, pageSize: Long): StaffIdsPage = api.getStaffIdsFromId(staffId = lastStaffId, size = pageSize.toInt())
    .awaitSingle()
}
