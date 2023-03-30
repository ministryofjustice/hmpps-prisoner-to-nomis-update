package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.annotation.JsonFormat
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNotFound
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun createVisit(request: CreateVisitDto): CreateVisitResponseDto =
    webClient.post()
      .uri("/prisoners/${request.offenderNo}/visits")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun cancelVisit(request: CancelVisitDto) {
    webClient.put()
      .uri("/prisoners/${request.offenderNo}/visits/${request.nomisVisitId}/cancel")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        log.warn("cancelVisit failed for offender no ${request.offenderNo} and Nomis visit id ${request.nomisVisitId} with message ${it.message}")
        Mono.empty()
      }
      .awaitSingleOrNull()
  }

  suspend fun createIncentive(bookingId: Long, request: CreateIncentiveDto): CreateIncentiveResponseDto =
    webClient.post()
      .uri("/prisoners/booking-id/$bookingId/incentives")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateVisit(offenderNo: String, nomisVisitId: String, updateVisitDto: UpdateVisitDto) {
    webClient.put()
      .uri("/prisoners/$offenderNo/visits/$nomisVisitId")
      .bodyValue(updateVisitDto)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createActivity(request: CreateActivityRequest): CreateActivityResponse =
    webClient.post()
      .uri("/activities")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateActivity(courseActivityId: Long, request: UpdateActivityRequest) {
    webClient.put()
      .uri("/activities/$courseActivityId")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun updateScheduleInstances(courseActivityId: Long, request: List<ScheduleRequest>) {
    webClient.put()
      .uri("/activities/$courseActivityId/schedules")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createAllocation(
    courseActivityId: Long,
    request: CreateOffenderProgramProfileRequest,
  ): OffenderProgramProfileResponse =
    webClient.post()
      .uri("/activities/$courseActivityId")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun deallocate(
    courseActivityId: Long,
    bookingId: Long,
    request: EndOffenderProgramProfileRequest,
  ): OffenderProgramProfileResponse =
    webClient.put()
      .uri("/activities/$courseActivityId/booking-id/$bookingId/end")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun createAttendance(
    scheduleId: Long,
    bookingId: Long,
    request: CreateAttendanceRequest,
  ) =
    webClient.post()
      .uri("/schedules/$scheduleId/booking/$bookingId/attendance")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun createAppointment(request: CreateAppointmentRequest): CreateAppointmentResponse =
    webClient.post()
      .uri("/appointments")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun createSentenceAdjustment(
    bookingId: Long,
    sentenceSequence: Long,
    request: CreateSentencingAdjustmentRequest,
  ): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateSentenceAdjustment(
    adjustmentId: Long,
    request: UpdateSentencingAdjustmentRequest,
  ): Unit =
    webClient.put()
      .uri("/sentence-adjustments/$adjustmentId")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun createKeyDateAdjustment(
    bookingId: Long,
    request: CreateSentencingAdjustmentRequest,
  ): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/prisoners/booking-id/$bookingId/adjustments")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateKeyDateAdjustment(
    adjustmentId: Long,
    request: UpdateSentencingAdjustmentRequest,
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

  suspend fun getGlobalIncentiveLevel(incentiveLevel: String): ReferenceCode? =
    webClient.get()
      .uri("/reference-domains/domains/IEP_LEVEL/codes/$incentiveLevel")
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun updateGlobalIncentiveLevel(incentiveLevel: ReferenceCode) {
    webClient.put()
      .uri("/reference-domains/domains/IEP_LEVEL/codes/${incentiveLevel.code}")
      .bodyValue(incentiveLevel)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createGlobalIncentiveLevel(incentiveLevel: ReferenceCode) =
    webClient.post()
      .uri("/reference-domains/domains/IEP_LEVEL/codes")
      .bodyValue(incentiveLevel)
      .retrieve()
      .awaitBodilessEntity()
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
  val schedules: List<ScheduleRequest>,
  val scheduleRules: List<ScheduleRuleRequest>,
)

data class UpdateActivityRequest(
  @JsonFormat(pattern = "yyyy-MM-dd")
  val endDate: LocalDate? = null,
  val internalLocationId: Long? = null,
  val payRates: List<PayRateRequest>,
  val scheduleRules: List<ScheduleRuleRequest>,
)

data class PayRateRequest(
  val incentiveLevel: String,
  val payBand: String,
  val rate: BigDecimal,
)

data class ScheduleRuleRequest(
  @JsonFormat(pattern = "HH:mm")
  val startTime: LocalTime,
  @JsonFormat(pattern = "HH:mm")
  val endTime: LocalTime,
  val monday: Boolean,
  val tuesday: Boolean,
  val wednesday: Boolean,
  val thursday: Boolean,
  val friday: Boolean,
  val saturday: Boolean,
  val sunday: Boolean,
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

data class CreateAttendanceRequest(
  val eventStatusCode: String,
  val eventOutcomeCode: String? = null,
  val comments: String? = null,
  val unexcusedAbsence: Boolean = false,
  val authorisedAbsence: Boolean = false,
  val paid: Boolean = false,
  val bonusPay: BigDecimal? = null,
)

data class CreateAppointmentRequest(
  val bookingId: Long,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val eventDate: LocalDate,
  @JsonFormat(pattern = "HH:mm")
  val startTime: LocalTime,
  @JsonFormat(pattern = "HH:mm")
  val endTime: LocalTime,
  val internalLocationId: Long? = null, // in cell if null
  val eventSubType: String,
)

data class CreateAppointmentResponse(
  val eventId: Long,
)

data class CreateSentencingAdjustmentRequest(
  val adjustmentTypeCode: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentDate: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentFromDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
)

data class ScheduleRequest(
  @JsonFormat(pattern = "yyyy-MM-dd")
  val date: LocalDate,
  @JsonFormat(pattern = "HH:mm")
  val startTime: LocalTime,
  @JsonFormat(pattern = "HH:mm")
  val endTime: LocalTime,
)

data class UpdateSentencingAdjustmentRequest(
  val adjustmentTypeCode: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentDate: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentFromDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
)

data class CreateSentencingAdjustmentResponse(val id: Long)

data class CreateVisitResponseDto(
  val visitId: String,
)

data class ReferenceCode(
  val code: String,
  val domain: String,
  val description: String,
  val active: Boolean,
)
