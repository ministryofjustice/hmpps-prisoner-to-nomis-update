package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.CourtScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.CourtSchedulerPrisonerResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingDto
import java.util.UUID

@Service
class CourtSchedulerMappingApiService(@Qualifier("mappingWebClient") webClient: WebClient) {

  private val prisonerApi = CourtSchedulerPrisonerResourceApi(webClient)
  private val scheduleApi = CourtScheduleResourceApi(webClient)

  suspend fun getCourtSchedulerPrisonMappingIds(offenderNo: String) = prisonerApi.getAllCourtSchedulerPrisonerMappingIds(offenderNo).awaitSingle()

  suspend fun getCourtScheduleMapping(id: UUID) = scheduleApi.getCourtScheduleMappingByDpsId(id).awaitBodyOrNullForNotFound()

  suspend fun createCourtScheduleMapping(mapping: CourtScheduleMappingDto) = scheduleApi.createCourtScheduleMapping(mapping).awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteCourtScheduleMapping(id: UUID) = scheduleApi.deleteCourtScheduleMappingByDpsId(id).awaitSingle()
}
