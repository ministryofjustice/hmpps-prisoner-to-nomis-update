package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.BedAssignmentMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.BedAssignmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PrisonerBedAssignmentMappingsDto

@Service
class BedAssignmentMappingApiService(
  @Qualifier("mappingWebClient")
  webClient: WebClient,
) {
  private val api = BedAssignmentMappingResourceApi(webClient)

  suspend fun create(mapping: BedAssignmentMappingDto) = api
    .createBedAssignmentMapping(mapping)
    .awaitSingle()

  suspend fun createMappingsForPrisoner(offenderNo: String, mappings: PrisonerBedAssignmentMappingsDto) = api
    .createBedAssignmentMappingsForPrisoner(offenderNo, mappings)
    .awaitSingle()

  suspend fun getMappingByNomisId(bookingId: Long, sequence: Int) = api
    .getBedAssignmentMappingByNomisId(bookingId, sequence)
    .awaitSingle()

  suspend fun getMappingByNomisIdOrNull(bookingId: Long, sequence: Int): BedAssignmentMappingDto? = api
    .getBedAssignmentMappingByNomisId(bookingId, sequence)
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingByDpsId(dpsBedAssignmentId: String) = api
    .getBedAssignmentMappingByDpsId(dpsBedAssignmentId)
    .awaitSingle()

  suspend fun getMappingByDpsIdOrNull(dpsBedAssignmentId: String): BedAssignmentMappingDto? = api
    .getBedAssignmentMappingByDpsId(dpsBedAssignmentId)
    .awaitBodyOrNullForNotFound()

  suspend fun deleteByDpsId(dpsBedAssignmentId: String) = api
    .deleteBedAssignmentMappingByDpsId(dpsBedAssignmentId)
    .awaitSingle()
}
