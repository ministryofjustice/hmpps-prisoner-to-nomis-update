package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class ExternalMovementsMappingApiService(
  @Qualifier("mappingWebClient") val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val domainUrl = "/mapping/temporary-absence"
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "ExternalMovementsMappingApiService"),
  )

  suspend fun createApplicationMapping(mapping: TemporaryAbsenceApplicationSyncMappingDto) = webClient.createMapping("$domainUrl/application", mapping)

  suspend fun createOutsideMovementMapping(mapping: TemporaryAbsenceOutsideMovementSyncMappingDto) = webClient.createMapping("$domainUrl/outside-movement", mapping)

  suspend fun createScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto) = webClient.createMapping("$domainUrl/scheduled-movement", mapping)

  suspend fun createExternalMovementMapping(mapping: ExternalMovementSyncMappingDto) = webClient.createMapping("$domainUrl/external-movement", mapping)

  private suspend inline fun <reified T : Any> WebClient.createMapping(url: String, mapping: T) = post()
    .uri(url)
    .bodyValue(mapping)
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()
}
