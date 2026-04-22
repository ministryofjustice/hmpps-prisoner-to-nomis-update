package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TapApplicationResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TapScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TemporaryAbsenceResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import java.util.*

@Service
class ExternalMovementsMappingApiService(
  @Qualifier("mappingWebClient") val webClient: WebClient,
) {
  private val mappingApi = TemporaryAbsenceResourceApi(webClient)
  private val applicationApi = TapApplicationResourceApi(webClient)
  private val scheduleApi = TapScheduleResourceApi(webClient)

  suspend fun createTapApplicationMapping(mapping: TapApplicationMappingDto) = applicationApi
    .prepare(applicationApi.createTapApplicationMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getTapApplicationMapping(dpsId: UUID): TapApplicationMappingDto? = applicationApi
    .prepare(applicationApi.getTapApplicationSyncMappingByDpsIdRequestConfig(dpsId))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createTapScheduleMapping(mapping: TapScheduleMappingDto) = scheduleApi
    .prepare(scheduleApi.createTapScheduleMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun updateTapScheduledMapping(mapping: TapScheduleMappingDto): Unit = scheduleApi
    .updateTapScheduleMapping(mapping)
    .awaitSingle()

  suspend fun getTapScheduleMapping(dpsId: UUID): TapScheduleMappingDto? = scheduleApi
    .prepare(scheduleApi.getTapScheduleMappingByDpsIdRequestConfig(dpsId))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createExternalMovementMapping(mapping: ExternalMovementSyncMappingDto) = mappingApi
    .prepare(mappingApi.createExternalMovementSyncMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getExternalMovementMapping(dpsId: UUID): ExternalMovementSyncMappingDto? = mappingApi
    .prepare(mappingApi.getExternalMovementSyncMappingByDpsIdRequestConfig(dpsId))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getTapMappingIds(offenderNo: String): TemporaryAbsencesPrisonerMappingIdsDto = mappingApi
    .getTemporaryAbsenceMappings(offenderNo)
    .awaitSingle()
}
