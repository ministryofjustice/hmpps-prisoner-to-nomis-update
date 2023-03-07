package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.LocalTime

@Service
class AppointmentsApiService(@Qualifier("appointmentsApiWebClient") private val webClient: WebClient) {

  suspend fun getAppointment(id: Long): Appointment {
    return webClient.get()
      .uri("/appointments/$id") // TODO Guess at endpoint
      .retrieve()
      .bodyToMono(Appointment::class.java)
      .awaitSingle()
  }
}

// TODO dummy for now
data class Appointment(
  val id: Long,
  val bookingId: Long,
  val locationId: Long,
  val date: LocalDate,
  val start: LocalTime,
  val end: LocalTime,
  val eventSubType: String, // TODO will have a mapping
)
