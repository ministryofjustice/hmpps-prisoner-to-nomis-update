package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.annotation.JsonFormat
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  fun createVisit(request: CreateVisitDto): CreateVisitResponseDto {
    return webClient.post()
      .uri("/prisoners/${request.offenderNo}/visits")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(CreateVisitResponseDto::class.java)
      .block()!!
  }

  fun cancelVisit(request: CancelVisitDto) {
    webClient.put()
      .uri("/prisoners/${request.offenderNo}/visits/vsipVisitId/${request.visitId}/cancel")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .block()
  }
}

data class CreateVisitDto(
  val offenderNo: String,
  val prisonId: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val startDateTime: LocalDateTime,
  val endTime: LocalTime,
  val visitorPersonIds: List<Long>,
  val decrementBalance: Boolean = true,
  val visitType: String,
  // val visitRoomId: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val issueDate: LocalDate,
  val vsipVisitId: String
)

data class CancelVisitDto(
  val offenderNo: String,
  val visitId: String,
  val outcome: String
)

data class CreateVisitResponseDto(
  val visitId: String
)
