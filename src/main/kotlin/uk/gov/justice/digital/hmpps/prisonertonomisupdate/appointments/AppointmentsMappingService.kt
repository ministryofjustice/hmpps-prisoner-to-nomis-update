package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Service
class AppointmentMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient
) {
  suspend fun createMapping(request: AppointmentMappingDto) {
    webClient.post()
      .uri("/mapping/appointments")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .awaitSingleOrNull()
  }

  suspend fun getMappingGivenAppointmentInstanceIdOrNull(id: Long): AppointmentMappingDto? =
    webClient.get()
      .uri("/mapping/appointments/appointment-instance-id/$id")
      .retrieve()
      .bodyToMono(AppointmentMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()

  suspend fun getMappingGivenAppointmentInstanceId(id: Long): AppointmentMappingDto =
    webClient.get()
      .uri("/mapping/appointments/appointment-instance-id/$id")
      .retrieve()
      .bodyToMono(AppointmentMappingDto::class.java)
      .awaitSingle()
}

data class AppointmentMappingDto(
  val appointmentInstanceId: Long,
  val nomisEventId: Long,
  val whenCreated: LocalDateTime? = null
)
