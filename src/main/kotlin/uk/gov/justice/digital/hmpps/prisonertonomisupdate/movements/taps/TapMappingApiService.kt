package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TapApplicationResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TapMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TapPrisonerResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TapScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapScheduleMappingDto
import java.util.UUID

@Service
class TapMappingApiService(
  @Qualifier("mappingWebClient") val webClient: WebClient,
) {
  private val applicationApi = TapApplicationResourceApi(webClient)
  private val scheduleApi = TapScheduleResourceApi(webClient)
  private val movementApi = TapMovementResourceApi(webClient)
  private val prisonerApi = TapPrisonerResourceApi(webClient)

  suspend fun createTapApplicationMapping(mapping: TapApplicationMappingDto) = applicationApi
    .prepare(applicationApi.createTapApplicationMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getTapApplicationMapping(dpsId: UUID): TapApplicationMappingDto? = applicationApi
    .getTapApplicationSyncMappingByDpsId(dpsId)
    .awaitBodyOrNullForNotFound()

  suspend fun createTapScheduleMapping(mapping: TapScheduleMappingDto) = scheduleApi
    .prepare(scheduleApi.createTapScheduleMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun updateTapScheduledMapping(mapping: TapScheduleMappingDto): Unit = scheduleApi
    .updateTapScheduleMapping(mapping)
    .awaitSingle()

  suspend fun getTapScheduleMapping(dpsId: UUID): TapScheduleMappingDto? = scheduleApi
    .getTapScheduleMappingByDpsId(dpsId)
    .awaitBodyOrNullForNotFound()

  suspend fun createTapMovementMapping(mapping: TapMovementMappingDto) = movementApi
    .prepare(movementApi.createTapMovementMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getTapMovementMapping(dpsId: UUID): TapMovementMappingDto? = movementApi
    .getTapMovementMappingByDpsId(dpsId)
    .awaitBodyOrNullForNotFound()

  suspend fun getTapMappingIds(offenderNo: String): TapPrisonerMappingIdsDto = prisonerApi
    .getAllPrisonerMappingIds(offenderNo)
    .awaitSingle()
}
