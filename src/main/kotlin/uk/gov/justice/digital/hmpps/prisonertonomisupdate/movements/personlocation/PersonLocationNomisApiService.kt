package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.OffenderPersonLocationsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderPersonLocationsResponse

@Service
class PersonLocationNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
) {

  private val offenderApi = OffenderPersonLocationsResourceApi(webClient)

  suspend fun getOffenderPersonLocationsOrNull(offenderNo: String): OffenderPersonLocationsResponse? = offenderApi.prepare(offenderApi.getOffenderPersonLocationsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
