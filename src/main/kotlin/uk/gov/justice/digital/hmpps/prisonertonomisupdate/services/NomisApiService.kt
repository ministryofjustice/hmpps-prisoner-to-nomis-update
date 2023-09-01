package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourseScheduleRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateActivityResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateActivityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCourseScheduleResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateRepairsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateRepairsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAllocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAttendanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertAttendanceResponse
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

  suspend fun updateActivity(courseActivityId: Long, request: UpdateActivityRequest): CreateActivityResponse =
    webClient.put()
      .uri("/activities/$courseActivityId")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateScheduledInstance(courseActivityId: Long, request: CourseScheduleRequest): UpdateCourseScheduleResponse =
    webClient.put()
      .uri("/activities/$courseActivityId/schedule")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun upsertAllocation(
    courseActivityId: Long,
    request: UpsertAllocationRequest,
  ): UpsertAllocationResponse =
    webClient.put()
      .uri("/activities/$courseActivityId/allocation")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun upsertAttendance(
    courseScheduleId: Long,
    bookingId: Long,
    request: UpsertAttendanceRequest,
  ): UpsertAttendanceResponse =
    webClient.put()
      .uri("/schedules/$courseScheduleId/booking/$bookingId/attendance")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun createAppointment(request: CreateAppointmentRequest): CreateAppointmentResponse =
    webClient.post()
      .uri("/appointments")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateAppointment(nomisEventId: Long, request: UpdateAppointmentRequest) =
    webClient.put()
      .uri("/appointments/$nomisEventId")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun cancelAppointment(nomisEventId: Long) =
    webClient.put()
      .uri("/appointments/$nomisEventId/cancel")
      .retrieve()
      .awaitBodilessEntity()

  suspend fun uncancelAppointment(nomisEventId: Long) =
    webClient.put()
      .uri("/appointments/$nomisEventId/uncancel")
      .retrieve()
      .awaitBodilessEntity()

  suspend fun deleteAppointment(nomisEventId: Long) =
    webClient.delete()
      .uri("/appointments/$nomisEventId")
      .retrieve()
      .awaitBodilessEntity()

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
      .uri("/incentives/reference-codes/$incentiveLevel")
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun updateGlobalIncentiveLevel(incentiveLevel: ReferenceCode) {
    webClient.put()
      .uri("/incentives/reference-codes/${incentiveLevel.code}")
      .bodyValue(incentiveLevel)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createGlobalIncentiveLevel(incentiveLevel: ReferenceCode) =
    webClient.post()
      .uri("/incentives/reference-codes")
      .bodyValue(incentiveLevel)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun globalIncentiveLevelReorder(levels: List<String>) =
    webClient.post()
      .uri("/incentives/reference-codes/reorder")
      .bodyValue(ReorderRequest(levels))
      .retrieve()
      .awaitBodilessEntity()

  suspend fun getActivePrisoners(
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<ActivePrisonerId> =
    webClient.get()
      .uri {
        it.path("/prisoners/ids")
          .queryParam("active", true)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<ActivePrisonerId>>())
      .awaitSingle()

  suspend fun getCurrentIncentive(bookingId: Long): NomisIncentive? =
    webClient.get()
      .uri("/incentives/booking-id/{bookingId}/current", bookingId)
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun updatePrisonIncentiveLevel(prison: String, prisonIncentive: PrisonIncentiveLevelRequest) =
    webClient.put()
      .uri("/incentives/prison/$prison/code/${prisonIncentive.levelCode}")
      .bodyValue(prisonIncentive)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun createPrisonIncentiveLevel(prison: String, prisonIncentive: PrisonIncentiveLevelRequest) =
    webClient.post()
      .uri("/incentives/prison/$prison")
      .bodyValue(prisonIncentive)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun getPrisonIncentiveLevel(prison: String, code: String): PrisonIncentiveLevel? =
    webClient.get()
      .uri("/incentives/prison/$prison/code/$code")
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun deleteActivity(courseActivityId: Long) {
    webClient.delete()
      .uri("/activities/$courseActivityId")
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createAdjudication(offenderNo: String, request: CreateAdjudicationRequest): AdjudicationResponse =
    webClient.post()
      .uri("/prisoners/$offenderNo/adjudications")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateAdjudicationRepairs(adjudicationNumber: Long, request: UpdateRepairsRequest): UpdateRepairsResponse =
    webClient.put()
      .uri("/adjudications/adjudication-number/$adjudicationNumber/repairs")
      .bodyValue(request)
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

data class CreateAppointmentRequest(
  val bookingId: Long,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val eventDate: LocalDate,
  @JsonFormat(pattern = "HH:mm")
  val startTime: LocalTime,
  @JsonFormat(pattern = "HH:mm")
  val endTime: LocalTime?,
  val internalLocationId: Long? = null, // in cell if null
  val eventSubType: String,
  val comment: String? = null,
)

data class CreateAppointmentResponse(
  val eventId: Long,
)

data class UpdateAppointmentRequest(
  @JsonFormat(pattern = "yyyy-MM-dd")
  val eventDate: LocalDate,
  @JsonFormat(pattern = "HH:mm")
  val startTime: LocalTime,
  @JsonFormat(pattern = "HH:mm")
  val endTime: LocalTime?,
  val internalLocationId: Long? = null, // in cell if null
  val eventSubType: String,
  val comment: String? = null,
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

data class ReorderRequest(
  val codeList: List<String>,
)

data class ReferenceCode(
  val code: String,
  val domain: String,
  val description: String,
  val active: Boolean,
)

data class ActivePrisonerId(
  val bookingId: Long,
  val offenderNo: String,
)

data class NomisIncentive(
  val iepLevel: NomisCodeDescription,
)

data class NomisCodeDescription(val code: String, val description: String)

class RestResponsePage<T>
@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
constructor(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("number") number: Int,
  @JsonProperty("size") size: Int,
  @JsonProperty("totalElements") totalElements: Long,
  @Suppress("UNUSED_PARAMETER")
  @JsonProperty(
    "pageable",
  )
  pageable: JsonNode,
) : PageImpl<T>(content, PageRequest.of(number, size), totalElements)

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}

data class PrisonIncentiveLevelRequest(
  val levelCode: String,
  val active: Boolean,
  val defaultOnAdmission: Boolean,
  val visitOrderAllowance: Int?,
  val privilegedVisitOrderAllowance: Int?,
  val remandTransferLimitInPence: Int? = null,
  val remandSpendLimitInPence: Int? = null,
  val convictedTransferLimitInPence: Int? = null,
  val convictedSpendLimitInPence: Int? = null,
)

data class PrisonIncentiveLevel(
  val prisonId: String,
  val iepLevelCode: String,
  val visitOrderAllowance: Int?,
  val privilegedVisitOrderAllowance: Int?,
  val defaultOnAdmission: Boolean,
  val remandTransferLimitInPence: Int?,
  val remandSpendLimitInPence: Int?,
  val convictedTransferLimitInPence: Int?,
  val convictedSpendLimitInPence: Int?,
  val active: Boolean,
  val expiryDate: LocalDate?,
  val visitAllowanceActive: Boolean?,
  val visitAllowanceExpiryDate: LocalDate?,
)
