package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Location

@Service
class LocationsApiService(@Qualifier("locationsApiWebClient") private val webClient: WebClient) {
  suspend fun getLocation(id: String): Location {
    return webClient.get()
      .uri("/locations/{id}", id)
      .retrieve()
      .bodyToMono(Location::class.java)
      .awaitSingle()
  }
}
