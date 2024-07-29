package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.net.URI

@Service
class LocationsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
  @Value("\${hmpps.web-client.locations.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value("\${hmpps.web-client.locations.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

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

  suspend fun getMappingGivenNomisIdOrNull(id: Long): LocationMappingDto? {
    lateinit var url: URI
    return webClient.get()
      .uri {
        url = it.path("/mapping/locations/nomis/{id}")
          .build(id)
        url
      }
      .retrieve()
      .bodyToMono<LocationMappingDto>()
      .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
      .retryWhen(backoffSpec.withRetryContext(Context.of("api", "mapping-api", "url", url.path)))
      .awaitSingleOrNull()
  }

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
