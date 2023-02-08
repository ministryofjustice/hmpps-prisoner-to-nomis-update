package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.annotation.JsonFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createVisit(request: CreateVisitDto): String =
    webClient.post()
      .uri("/prisoners/${request.offenderNo}/visits")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(CreateVisitResponseDto::class.java)
      .block()!!.visitId

  fun cancelVisit(request: CancelVisitDto) {
    webClient.put()
      .uri("/prisoners/${request.offenderNo}/visits/${request.nomisVisitId}/cancel")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        log.warn("cancelVisit failed for offender no ${request.offenderNo} and Nomis visit id ${request.nomisVisitId} with message ${it.message}")
        Mono.empty()
      }
      .block()
  }

  fun createIncentive(bookingId: Long, request: CreateIncentiveDto): CreateIncentiveResponseDto =
    webClient.post()
      .uri("/prisoners/booking-id/$bookingId/incentives")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(CreateIncentiveResponseDto::class.java)
      .block()!!

  fun updateVisit(offenderNo: String, nomisVisitId: String, updateVisitDto: UpdateVisitDto) {
    webClient.put()
      .uri("/prisoners/$offenderNo/visits/$nomisVisitId")
      .bodyValue(updateVisitDto)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .block()
  }

  fun createActivity(request: CreateActivityRequest): CreateActivityResponse =
    webClient.post()
      .uri("/activities")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(CreateActivityResponse::class.java)
      .block()!!

  fun updateActivity(courseActivityId: Long, request: UpdateActivityRequest) {
    webClient.put()
      .uri("/activities/$courseActivityId")
      .bodyValue(request)
      .retrieve()
      .toBodilessEntity()
      .block()!!
  }

  fun createAllocation(
    courseActivityId: Long,
    request: CreateOffenderProgramProfileRequest
  ): OffenderProgramProfileResponse =
    webClient.post()
      .uri("/activities/$courseActivityId")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(OffenderProgramProfileResponse::class.java)
      .block()!!

  fun deallocate(
    courseActivityId: Long,
    bookingId: Long,
    request: EndOffenderProgramProfileRequest
  ): OffenderProgramProfileResponse =
    webClient.put()
      .uri("/activities/$courseActivityId/booking-id/$bookingId/end")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(OffenderProgramProfileResponse::class.java)
      .block()!!

  suspend fun createSentenceAdjustment(
    bookingId: Long,
    sentenceSequence: Long,
    request: CreateSentencingAdjustmentRequest
  ): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateSentenceAdjustment(
    adjustmentId: Long,
    request: UpdateSentencingAdjustmentRequest
  ): Unit =
    webClient.put()
      .uri("/sentence-adjustments/$adjustmentId")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun createKeyDateAdjustment(
    bookingId: Long,
    request: CreateSentencingAdjustmentRequest
  ): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/prisoners/booking-id/$bookingId/adjustments")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateKeyDateAdjustment(
    adjustmentId: Long,
    request: UpdateSentencingAdjustmentRequest
  ): Unit =
    webClient.put()
      .uri("/key-date-adjustments/$adjustmentId")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun deleteSentenceAdjustment(adjustmentId: Long): Unit =
    webClient.delete()
      .uri("/sentence-adjustments/$adjustmentId")
      .retrieve()
      .awaitBody()

  suspend fun deleteKeyDateAdjustment(adjustmentId: Long): Unit =
    webClient.delete()
      .uri("/key-date-adjustments/$adjustmentId")
      .retrieve()
      .awaitBody()
}

data class CreateVisitDto(
  val offenderNo: String,
  val prisonId: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val startDateTime: LocalDateTime,
  @JsonFormat(pattern = "HH:mm:ss")
  val endTime: LocalTime,
  val visitorPersonIds: List<Long>,
  val decrementBalance: Boolean = true,
  val visitType: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val issueDate: LocalDate,
  val visitComment: String,
  val visitOrderComment: String,
  val room: String,
  val openClosedStatus: String,
)

data class CancelVisitDto(
  val offenderNo: String,
  val nomisVisitId: String,
  val outcome: String,
)

data class UpdateVisitDto(
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val startDateTime: LocalDateTime,
  @JsonFormat(pattern = "HH:mm:ss")
  val endTime: LocalTime,
  val visitorPersonIds: List<Long>,
  val room: String,
  val openClosedStatus: String,
)

data class CreateVisitResponseDto(
  val visitId: String,
)

data class CreateIncentiveDto(
  val comments: String? = null,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val iepDateTime: LocalDateTime,
  val prisonId: String,
  val iepLevel: String,
  val userId: String? = null,
)

data class CreateIncentiveResponseDto(
  val bookingId: Long,
  val sequence: Long,
)

data class CreateActivityRequest(
  val code: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val startDate: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val endDate: LocalDate? = null,
  val prisonId: String,
  val internalLocationId: Long? = null,
  val capacity: Int,
  val payRates: List<PayRateRequest>,
  val description: String,
  val minimumIncentiveLevelCode: String? = null,
  val programCode: String,
  val payPerSession: String,
)
data class UpdateActivityRequest(
  @JsonFormat(pattern = "yyyy-MM-dd")
  val endDate: LocalDate? = null,
  val internalLocationId: Long? = null,
  val payRates: List<PayRateRequest>,
)

data class PayRateRequest(
  val incentiveLevel: String,
  val payBand: String,
  val rate: BigDecimal,
)

data class CreateActivityResponse(
  val courseActivityId: Long,
)

data class CreateOffenderProgramProfileRequest(
  val bookingId: Long,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val startDate: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val endDate: LocalDate? = null,
  val payBandCode: String,
)

data class OffenderProgramProfileResponse(
  val offenderProgramReferenceId: Long,
)

data class EndOffenderProgramProfileRequest(
  @JsonFormat(pattern = "yyyy-MM-dd")
  val endDate: LocalDate,
  val endReason: String? = null,
  val endComment: String? = null,
)

data class CreateSentencingAdjustmentRequest(
  val adjustmentTypeCode: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentDate: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentFomDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
)

data class UpdateSentencingAdjustmentRequest(
  val adjustmentTypeCode: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentDate: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentFomDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
)

data class CreateSentencingAdjustmentResponse(val id: Long)
