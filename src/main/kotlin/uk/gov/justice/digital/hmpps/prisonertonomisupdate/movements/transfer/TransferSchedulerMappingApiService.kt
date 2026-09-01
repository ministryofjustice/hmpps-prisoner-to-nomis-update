package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TransferScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TransferSchedulerPrisonerResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import java.util.UUID

@Service
class TransferSchedulerMappingApiService(@Qualifier("mappingWebClient") webClient: WebClient) {

  private val prisonerApi = TransferSchedulerPrisonerResourceApi(webClient)
  private val scheduleApi = TransferScheduleResourceApi(webClient)

  suspend fun getMappings(offenderNo: String): TransferSchedulerPrisonerMappingIdsDto = prisonerApi.getAllTransferSchedulerPrisonerMappingIds(offenderNo).awaitSingle()

  suspend fun getTransferScheduleMapping(id: UUID) = scheduleApi.getTransferScheduleMappingByDpsId(id).awaitBodyOrNullForNotFound()

  suspend fun deleteTransferScheduleMapping(id: UUID) = scheduleApi.deleteTransferScheduleMappingByDpsId(id).awaitSingle()
}
