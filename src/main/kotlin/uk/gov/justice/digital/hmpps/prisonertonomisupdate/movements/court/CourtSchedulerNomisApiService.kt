package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.CourtScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.OffenderCourtMovementsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderCourtMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOutResponse

@Service
class CourtSchedulerNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
) {

  private val offenderApi = OffenderCourtMovementsResourceApi(webClient)
  private val scheduleApi = CourtScheduleResourceApi(webClient)

  suspend fun getOffenderCourtMovementsOrNull(offenderNo: String): OffenderCourtMovementsResponse? = offenderApi.prepare(offenderApi.getOffenderCourtMovementsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun upsertCourtScheduleOut(prisonerNumber: String, request: UpsertCourtScheduleOut): UpsertCourtScheduleOutResponse = scheduleApi.upsertCourtScheduleOut(prisonerNumber, request).awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteCourtScheduleOut(prisonerNumber: String, eventId: Long) = scheduleApi.deleteCourtScheduleOut(prisonerNumber, eventId).awaitSingle()
}
