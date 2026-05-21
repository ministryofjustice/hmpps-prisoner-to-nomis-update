package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.CourtSchedulerPrisonerResourceApi

@Service
class CourtSchedulerMappingApiService(@Qualifier("mappingWebClient") webClient: WebClient) {

  private val prisonerApi = CourtSchedulerPrisonerResourceApi(webClient)

  suspend fun getCourtSchedulerPrisonMappingIds(offenderNo: String) = prisonerApi.getAllCourtSchedulerPrisonerMappingIds(offenderNo).awaitSingle()
}
