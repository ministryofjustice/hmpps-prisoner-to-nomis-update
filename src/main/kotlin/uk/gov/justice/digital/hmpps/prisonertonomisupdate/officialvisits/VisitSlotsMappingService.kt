package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.VisitSlotsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitTimeSlotMappingDto

@Service
class VisitSlotsMappingService(
  @Qualifier("mappingWebClient")
  webClient: WebClient,
) {
  private val api = VisitSlotsResourceApi(webClient)

  suspend fun getTimeSlotByDpsIdOrNull(dpsId: String): VisitTimeSlotMappingDto? = api.prepare(
    api.getVisitTimeSlotMappingByDpsIdRequestConfig(dpsId = dpsId),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createTimeSlotMapping(mapping: VisitTimeSlotMappingDto) = api.prepare(api.createVisitTimeSlotMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getVisitSlotByDpsIdOrNull(dpsId: String): VisitSlotMappingDto? = api.prepare(
    api.getVisitSlotMappingByDpsIdRequestConfig(dpsId = dpsId),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createVisitSlotMapping(mapping: VisitSlotMappingDto) = api.prepare(api.createVisitSlotMappingRequestConfig(mapping))
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()
}
