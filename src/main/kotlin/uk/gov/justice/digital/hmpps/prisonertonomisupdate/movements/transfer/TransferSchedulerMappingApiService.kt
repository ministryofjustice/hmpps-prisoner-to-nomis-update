package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TransferSchedulerPrisonerResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto

@Service
class TransferSchedulerMappingApiService(@Qualifier("mappingWebClient") webClient: WebClient) {

  private val prisonerApi = TransferSchedulerPrisonerResourceApi(webClient)

  suspend fun getMappings(offenderNo: String): TransferSchedulerPrisonerMappingIdsDto = prisonerApi.getAllTransferSchedulerPrisonerMappingIds(offenderNo).awaitSingle()
}
