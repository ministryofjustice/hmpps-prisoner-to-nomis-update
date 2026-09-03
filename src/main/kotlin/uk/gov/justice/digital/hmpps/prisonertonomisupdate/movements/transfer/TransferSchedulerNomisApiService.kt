package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.OffenderTransferMovementsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.TransferScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransferMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleOutResponse

@Service
class TransferSchedulerNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
) {

  private val offenderApi = OffenderTransferMovementsResourceApi(webClient)
  private val scheduleApi = TransferScheduleResourceApi(webClient)

  suspend fun getOffenderTransferMovementsOrNull(offenderNo: String): OffenderTransferMovementsResponse? = offenderApi.prepare(offenderApi.getOffenderTransferMovementsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun upsertTransferSchedule(offenderNo: String, request: UpsertTransferScheduleOut): UpsertTransferScheduleOutResponse = scheduleApi.upsertTransferScheduleOut(offenderNo, request).awaitBodyOrLogAndRethrowBadRequest()
}
