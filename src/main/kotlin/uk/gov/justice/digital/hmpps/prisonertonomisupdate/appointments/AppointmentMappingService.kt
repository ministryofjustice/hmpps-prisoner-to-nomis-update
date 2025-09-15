package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AppointmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.UUID

@Service
class AppointmentMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
  @Value("\${hmpps.web-client.appointments-mapping.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value("\${hmpps.web-client.appointments-mapping.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

  suspend fun createMapping(request: AppointmentMappingDto) {
    webClient
      .post()
      .uri("/mapping/appointments")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenAppointmentInstanceIdOrNull(id: Long): AppointmentMappingDto? = webClient
    .get()
    .uri("/mapping/appointments/appointment-instance-id/{id}", id)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenAppointmentInstanceId(id: Long): AppointmentMappingDto = webClient
    .get()
    .uri("/mapping/appointments/appointment-instance-id/{id}", id)
    .retrieve()
    .bodyToMono(AppointmentMappingDto::class.java)
    .awaitSingle()

  suspend fun getMappingGivenNomisIdOrNull(id: Long): AppointmentMappingDto? = webClient
    .get()
    .uri("/mapping/appointments/nomis-event-id/{id}", id)
    .retrieve()
    .awaitBodyOrNullForNotFound(backoffSpec)

  suspend fun deleteMapping(appointmentInstanceId: Long) {
    webClient
      .delete()
      .uri("/mapping/appointments/appointment-instance-id/{appointmentInstanceId}", appointmentInstanceId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getLocationMappingGivenDpsId(id: UUID): LocationMappingDto = webClient
    .get()
    .uri("/mapping/locations/dps/{id}", id.toString())
    .retrieve()
    .bodyToMono(LocationMappingDto::class.java)
    .awaitSingle()
}
