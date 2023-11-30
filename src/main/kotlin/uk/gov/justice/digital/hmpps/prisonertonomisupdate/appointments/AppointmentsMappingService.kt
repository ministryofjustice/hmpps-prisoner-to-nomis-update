package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import java.time.LocalDateTime

@Service
class AppointmentMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {
  suspend fun createMapping(request: AppointmentMappingDto) {
    webClient.post()
      .uri("/mapping/appointments")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenAppointmentInstanceIdOrNull(id: Long): AppointmentMappingDto? =
    webClient.get()
      .uri("/mapping/appointments/appointment-instance-id/{id}", id)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenAppointmentInstanceId(id: Long): AppointmentMappingDto =
    webClient.get()
      .uri("/mapping/appointments/appointment-instance-id/{id}", id)
      .retrieve()
      .bodyToMono(AppointmentMappingDto::class.java)
      .awaitSingle()

  suspend fun getAllMappings(): List<AppointmentMappingDto> =
    webClient.get()
      .uri("/mapping/appointments")
      .retrieve()
      .awaitBody()

  suspend fun deleteMapping(appointmentInstanceId: Long) {
    webClient.delete()
      .uri("/mapping/appointments/appointment-instance-id/{appointmentInstanceId}", appointmentInstanceId)
      .retrieve()
      .awaitBodilessEntity()
  }
}

data class AppointmentMappingDto(
  val appointmentInstanceId: Long,
  val nomisEventId: Long,
  val whenCreated: LocalDateTime? = null,
)
