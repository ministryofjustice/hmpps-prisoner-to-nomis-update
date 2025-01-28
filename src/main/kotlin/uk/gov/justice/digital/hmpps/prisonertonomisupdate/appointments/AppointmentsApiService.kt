package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance

@Service
class AppointmentsApiService(@Qualifier("appointmentsApiWebClient") private val webClient: WebClient) {
  suspend fun getAppointmentInstance(id: Long): AppointmentInstance = webClient.get()
    .uri("/appointment-instances/{id}", id)
    .retrieve()
    .bodyToMono(AppointmentInstance::class.java)
    .awaitSingle()
}
