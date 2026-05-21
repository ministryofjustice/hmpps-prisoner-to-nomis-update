package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.OffenderCourtMovementsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderCourtMovementsResponse

@Service
class CourtSchedulerNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
) {

  private val offenderApi = OffenderCourtMovementsResourceApi(webClient)

  suspend fun getOffenderCourtMovementsOrNull(offenderNo: String): OffenderCourtMovementsResponse? = offenderApi.prepare(offenderApi.getOffenderCourtMovementsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
