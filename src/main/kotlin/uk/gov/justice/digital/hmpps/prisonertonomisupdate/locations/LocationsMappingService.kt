package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto

@Service
class LocationsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {
  suspend fun createMapping(request: LocationMappingDto) {
    webClient.post()
      .uri("/mapping/locations")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenDpsIdOrNull(id: String): LocationMappingDto? =
    webClient.get()
      .uri("/mapping/locations/dps/{id}", id)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenDpsId(id: String): LocationMappingDto =
    webClient.get()
      .uri("/mapping/locations/dps/{id}", id)
      .retrieve()
      .bodyToMono(LocationMappingDto::class.java)
      .awaitSingle()

  suspend fun getMappingGivenNomisId(id: Long): LocationMappingDto =
    webClient.get()
      .uri("/mapping/locations/nomis/{id}", id)
      .retrieve()
      .bodyToMono(LocationMappingDto::class.java)
      .awaitSingle()

  suspend fun getAllMappings(): List<LocationMappingDto> =
    webClient.get()
      .uri("/mapping/locations")
      .retrieve()
      .awaitBody()

  suspend fun deleteMapping(dpsId: String) {
    webClient.delete()
      .uri("/mapping/locations/dps/{dpsId}", dpsId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
