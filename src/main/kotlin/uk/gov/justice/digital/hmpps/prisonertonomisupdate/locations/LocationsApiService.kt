package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference

@Service
class LocationsApiService(@Qualifier("locationsApiWebClient") private val webClient: WebClient) {
  suspend fun getLocation(id: String, includeHistory: Boolean = false): LegacyLocation {
    return webClient.get()
      .uri {
        it.path("/sync/id/{id}")
          .queryParam("includeHistory", includeHistory)
          .build(id)
      }
      .retrieve()
      .bodyToMono(LegacyLocation::class.java)
      .awaitSingle()
  }

  suspend fun getLocations(
    pageNumber: Long,
    pageSize: Long,
  ): Page<LegacyLocation> {
    return webClient.get()
      .uri {
        it.path("/locations")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<LegacyLocation>>())
      .awaitSingle()
  }
}
