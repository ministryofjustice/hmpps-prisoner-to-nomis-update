package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.model.AppointmentOccurrenceDetails

@Service
class AppointmentsApiService(@Qualifier("appointmentsApiWebClient") private val webClient: WebClient) {

  suspend fun getAppointment(id: Long): AppointmentInstance {
    return webClient.get()
      .uri("/appointments/$id")
      .retrieve()
      .bodyToMono(AppointmentInstance::class.java)
      .awaitSingle()
  }

  suspend fun getAppointmentOccurrence(id: Long): AppointmentOccurrenceDetails {
    return webClient.get()
      .uri("/appointment-occurrence-details/$id")
      .retrieve()
      .bodyToMono(AppointmentOccurrenceDetails::class.java)
      .awaitSingle()
  }

  suspend fun getAppointmentInstance(id: Long): AppointmentInstance {
    return webClient.get()
      .uri("/appointment-instance-details/$id") // TODO Guess at endpoint as it doesnt exist yet
      .retrieve()
      .bodyToMono(AppointmentInstance::class.java)
      .awaitSingle()
  }
}
