package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference

@Service
class LocationsApiService(@Qualifier("locationsApiWebClient") private val webClient: WebClient) {
  suspend fun getLocation(id: String, includeHistory: Boolean = false): Location {
    return webClient.get()
      .uri {
        it.path("/locations/{id}")
          .queryParam("includeHistory", includeHistory)
          .build(id)
      }
      .retrieve()
      .bodyToMono(Location::class.java)
      .awaitSingle()
  }

  suspend fun getLocations(
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<Location> {
    return webClient.get()
      .uri {
        it.path("/locations")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<Location>>())
      .awaitSingle()
  }
}
