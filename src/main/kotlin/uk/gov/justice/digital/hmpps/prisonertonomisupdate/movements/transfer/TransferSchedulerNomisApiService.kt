package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.OffenderTransferMovementsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransferMovementsResponse

@Service
class TransferSchedulerNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
) {

  private val offenderApi = OffenderTransferMovementsResourceApi(webClient)

  suspend fun getOffenderTransferMovementsOrNull(offenderNo: String): OffenderTransferMovementsResponse? = offenderApi.prepare(offenderApi.getOffenderTransferMovementsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
