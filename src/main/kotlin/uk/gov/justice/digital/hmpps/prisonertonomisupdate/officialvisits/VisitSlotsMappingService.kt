package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.LocationMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.VisitSlotsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitTimeSlotMappingDto

@Service
class VisitSlotsMappingService(
  @Qualifier("mappingWebClient")
  webClient: WebClient,
) {
  private val api = VisitSlotsResourceApi(webClient)
  private val locationApi = LocationMappingResourceApi(webClient)

  suspend fun getTimeSlotByDpsIdOrNull(dpsId: String): VisitTimeSlotMappingDto? = api.prepare(
    api.getVisitTimeSlotMappingByDpsIdRequestConfig(dpsId = dpsId),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getTimeSlotByDpsId(dpsId: String): VisitTimeSlotMappingDto = api.getVisitTimeSlotMappingByDpsId(dpsId = dpsId).awaitSingle()

  suspend fun createTimeSlotMapping(mapping: VisitTimeSlotMappingDto) = api.prepare(api.createVisitTimeSlotMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteTimeSlotByNomisIds(nomisPrisonId: String, nomisDayOfWeek: String, nomisSlotSequence: Int) {
    api.deleteVisitTimeSlotMappingByNomisIds(
      nomisPrisonId = nomisPrisonId,
      nomisDayOfWeek = nomisDayOfWeek,
      nomisSlotSequence = nomisSlotSequence,
    )
      .awaitSingle()
  }

  suspend fun getVisitSlotByDpsIdOrNull(dpsId: String): VisitSlotMappingDto? = api.prepare(
    api.getVisitSlotMappingByDpsIdRequestConfig(dpsId = dpsId),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getVisitSlotByDpsId(visitSlotId: String) = api.getVisitSlotMappingByDpsId(dpsId = visitSlotId).awaitSingle()

  suspend fun createVisitSlotMapping(mapping: VisitSlotMappingDto) = api.prepare(api.createVisitSlotMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteVisitSlotByNomisId(nomisVisitSlotId: Long) {
    api.deleteVisitSlotMappingByNomisId(nomisId = nomisVisitSlotId).awaitSingle()
  }

  suspend fun getInternalLocationByDpsId(dpsLocationId: String): LocationMappingDto = locationApi.getMappingGivenDpsId1(dpsLocationId = dpsLocationId).awaitSingle()
}
