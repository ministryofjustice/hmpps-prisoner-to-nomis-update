package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.TemporaryAbsenceResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import java.util.*

@Service
class ExternalMovementsMappingApiService(
  @Qualifier("mappingWebClient") val webClient: WebClient,
) {
  private val mappingApi = TemporaryAbsenceResourceApi(webClient)

  suspend fun createApplicationMapping(mapping: TemporaryAbsenceApplicationSyncMappingDto) = mappingApi
    .prepare(mappingApi.createApplicationSyncMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getApplicationMapping(dpsId: UUID): TemporaryAbsenceApplicationSyncMappingDto? = mappingApi
    .prepare(mappingApi.getApplicationSyncMappingByDpsIdRequestConfig(dpsId))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto) = mappingApi
    .prepare(mappingApi.createScheduledMovementSyncMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun updateScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto): Unit = mappingApi
    .updateScheduledMovementSyncMapping(mapping)
    .awaitSingle()

  suspend fun getScheduledMovementMapping(dpsId: UUID): ScheduledMovementSyncMappingDto? = mappingApi
    .prepare(mappingApi.getScheduledMovementSyncMappingByDpsIdRequestConfig(dpsId))
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
}
