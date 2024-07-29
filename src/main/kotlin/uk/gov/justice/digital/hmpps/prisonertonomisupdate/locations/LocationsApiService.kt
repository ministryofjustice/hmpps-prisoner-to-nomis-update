package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference
import java.net.URI

@Service
class LocationsApiService(
  @Qualifier("locationsApiWebClient") private val webClient: WebClient,
  @Value("\${hmpps.web-client.casenotes.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value("\${hmpps.web-client.casenotes.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

  suspend fun getLocation(id: String, includeHistory: Boolean = false): LegacyLocation {
    lateinit var url: URI
    return webClient.get()
      .uri {
        url = it.path("/sync/id/{id}")
          .queryParam("includeHistory", includeHistory)
          .build(id)
        url
      }
      .retrieve()
      .bodyToMono(LegacyLocation::class.java)
      .retryWhen(backoffSpec.withRetryContext(Context.of("api", "locations-api", "url", url.path)))
      .awaitSingle()
  }

  suspend fun getLocations(
    pageNumber: Long,
    pageSize: Long,
  ): Page<LegacyLocation> {
    lateinit var url: URI
    return webClient.get()
      .uri {
        url = it.path("/locations")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
        url
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<LegacyLocation>>())
      .retryWhen(backoffSpec.withRetryContext(Context.of("api", "locations-api", "url", url.path)))
      .awaitSingle()
  }
}
