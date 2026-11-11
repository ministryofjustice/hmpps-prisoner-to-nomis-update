package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.StaffResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails

@Service
class StaffNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = StaffResourceApi(webClient)

  suspend fun getStaffDetails(staffId: Long): StaffDetails = api.getStaffById(staffId, true)
    .awaitSingle()

  suspend fun getAllStaffIdsInRange(fromStaffId: Long, toStaffId: Long) = api
    .getAllStaffInRange(false, fromStaffId, toStaffId)
    .awaitSingle()

  suspend fun getAllStaffIdRanges(pageSize: Long) = api
    .getAllStaffIdRanges(false, pageSize.toInt())
    .awaitSingle()
}
