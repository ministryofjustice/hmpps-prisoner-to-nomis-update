package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CourtAppearanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCourtCaseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingResultAwardResponses
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingResultRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateNonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.DeactivateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.DeleteHearingResultAwardResponses
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.DeleteHearingResultResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.Hearing
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.MergeDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.NonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UnquashHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCapacityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCertificationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateEvidenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateEvidenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateHearingResultAwardResponses
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateRepairsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateRepairsResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class NomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  @Value("\${hmpps.web-client.nomis.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value("\${hmpps.web-client.nomis.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

  // ////////// VISITS //////////////

  suspend fun createVisit(request: CreateVisitDto): CreateVisitResponseDto =
    webClient.post()
      .uri("/prisoners/{offenderNo}/visits", request.offenderNo)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun cancelVisit(request: CancelVisitDto) {
    webClient.put()
      .uri("/prisoners/{offenderNo}/visits/{nomisVisitId}/cancel", request.offenderNo, request.nomisVisitId)
      .bodyValue(request)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        log.warn("cancelVisit failed for offender no ${request.offenderNo} and Nomis visit id ${request.nomisVisitId} with message ${it.message}")
        Mono.empty()
      }
      .awaitSingleOrNull()
  }

  suspend fun updateVisit(offenderNo: String, nomisVisitId: String, updateVisitDto: UpdateVisitDto) {
    webClient.put()
      .uri("/prisoners/{offenderNo}/visits/{nomisVisitId}", offenderNo, nomisVisitId)
      .bodyValue(updateVisitDto)
      .retrieve()
      .awaitBodilessEntity()
  }

  // ////////// INCENTIVES //////////////

  suspend fun createIncentive(bookingId: Long, request: CreateIncentiveDto): CreateIncentiveResponseDto =
    webClient.post()
      .uri("/prisoners/booking-id/{bookingId}/incentives", bookingId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun getCurrentIncentive(bookingId: Long): NomisIncentive? =
    webClient.get()
      .uri("/incentives/booking-id/{bookingId}/current", bookingId)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  // //////////////////// APPOINTMENTS /////////////////////////

  suspend fun createAppointment(request: CreateAppointmentRequest): CreateAppointmentResponse =
    webClient.post()
      .uri("/appointments")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateAppointment(nomisEventId: Long, request: UpdateAppointmentRequest) =
    webClient.put()
      .uri("/appointments/{nomisEventId}", nomisEventId)
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun cancelAppointment(nomisEventId: Long) =
    webClient.put()
      .uri("/appointments/{nomisEventId}/cancel", nomisEventId)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun uncancelAppointment(nomisEventId: Long) =
    webClient.put()
      .uri("/appointments/{nomisEventId}/uncancel", nomisEventId)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun deleteAppointment(nomisEventId: Long) =
    webClient.delete()
      .uri("/appointments/{nomisEventId}", nomisEventId)
      .retrieve()
      .awaitBodilessEntity()

  // //////////////////// SENTENCES ////////////////////////

  suspend fun createSentenceAdjustment(
    bookingId: Long,
    sentenceSequence: Long,
    request: CreateSentencingAdjustmentRequest,
  ): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/prisoners/booking-id/{bookingId}/sentences/{sentenceSequence}/adjustments", bookingId, sentenceSequence)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateSentenceAdjustment(
    adjustmentId: Long,
    request: UpdateSentencingAdjustmentRequest,
  ): Unit =
    webClient.put()
      .uri("/sentence-adjustments/{adjustmentId}", adjustmentId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun createKeyDateAdjustment(
    bookingId: Long,
    request: CreateSentencingAdjustmentRequest,
  ): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/prisoners/booking-id/{bookingId}/adjustments", bookingId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateKeyDateAdjustment(
    adjustmentId: Long,
    request: UpdateSentencingAdjustmentRequest,
  ): Unit =
    webClient.put()
      .uri("/key-date-adjustments/{adjustmentId}", adjustmentId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun deleteSentenceAdjustment(adjustmentId: Long): Unit =
    webClient.delete()
      .uri("/sentence-adjustments/{adjustmentId}", adjustmentId)
      .retrieve()
      .awaitBody()

  suspend fun deleteKeyDateAdjustment(adjustmentId: Long): Unit =
    webClient.delete()
      .uri("/key-date-adjustments/{adjustmentId}", adjustmentId)
      .retrieve()
      .awaitBody()

  suspend fun getAdjustments(bookingId: Long): SentencingAdjustmentsResponse =
    webClient.get()
      .uri("/prisoners/booking-id/{bookingId}/sentencing-adjustments", bookingId)
      .retrieve()
      .awaitBody()

  // ////////// INCENTIVE LEVELS //////////////

  suspend fun getGlobalIncentiveLevel(incentiveLevel: String): ReferenceCode? =
    webClient.get()
      .uri("/incentives/reference-codes/{incentiveLevel}", incentiveLevel)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  suspend fun updateGlobalIncentiveLevel(incentiveLevel: ReferenceCode) {
    webClient.put()
      .uri("/incentives/reference-codes/{code}", incentiveLevel.code)
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
  ): PageImpl<PrisonerIds> =
    webClient.get()
      .uri {
        it.path("/prisoners/ids/active")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<PrisonerIds>>())
      .awaitSingle()

  suspend fun updatePrisonIncentiveLevel(prison: String, prisonIncentive: PrisonIncentiveLevelRequest) =
    webClient.put()
      .uri("/incentives/prison/{prison}/code/{levelCode}", prison, prisonIncentive.levelCode)
      .bodyValue(prisonIncentive)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun createPrisonIncentiveLevel(prison: String, prisonIncentive: PrisonIncentiveLevelRequest) =
    webClient.post()
      .uri("/incentives/prison/{prison}", prison)
      .bodyValue(prisonIncentive)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun getPrisonIncentiveLevel(prison: String, code: String): PrisonIncentiveLevel? =
    webClient.get()
      .uri("/incentives/prison/{prison}/code/{code}", prison, code)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  // ////////// ADJUDICATIONS //////////////

  suspend fun createAdjudication(offenderNo: String, request: CreateAdjudicationRequest): AdjudicationResponse =
    webClient.post()
      .uri("/prisoners/{offenderNo}/adjudications", offenderNo)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateAdjudicationRepairs(
    adjudicationNumber: Long,
    request: UpdateRepairsRequest,
  ): UpdateRepairsResponse =
    webClient.put()
      .uri("/adjudications/adjudication-number/{adjudicationNumber}/repairs", adjudicationNumber)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateAdjudicationEvidence(
    adjudicationNumber: Long,
    request: UpdateEvidenceRequest,
  ): UpdateEvidenceResponse =
    webClient.put()
      .uri("/adjudications/adjudication-number/{adjudicationNumber}/evidence", adjudicationNumber)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun createHearing(adjudicationNumber: Long, request: CreateHearingRequest): CreateHearingResponse =
    webClient.post()
      .uri("/adjudications/adjudication-number/{adjudicationNumber}/hearings", adjudicationNumber)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateHearing(adjudicationNumber: Long, hearingId: Long, request: UpdateHearingRequest): Hearing =
    webClient.put()
      .uri(
        "/adjudications/adjudication-number/{adjudicationNumber}/hearings/{hearingId}",
        adjudicationNumber,
        hearingId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun deleteHearing(adjudicationNumber: Long, hearingId: Long) {
    webClient.delete()
      .uri(
        "/adjudications/adjudication-number/{adjudicationNumber}/hearings/{hearingId}",
        adjudicationNumber,
        hearingId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun upsertHearingResult(
    adjudicationNumber: Long,
    hearingId: Long,
    chargeSequence: Int,
    request: CreateHearingResultRequest,
  ) = webClient.post()
    .uri(
      "/adjudications/adjudication-number/{adjudicationNumber}/hearings/{hearingId}/charge/{chargeSequence}/result",
      adjudicationNumber,
      hearingId,
      chargeSequence,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun deleteHearingResult(
    adjudicationNumber: Long,
    hearingId: Long,
    chargeSequence: Int,
  ): DeleteHearingResultResponse =
    webClient.delete()
      .uri(
        "/adjudications/adjudication-number/{adjudicationNumber}/hearings/{hearingId}/charge/{chargeSequence}/result",
        adjudicationNumber,
        hearingId,
        chargeSequence,
      )
      .retrieve()
      .awaitBody()

  suspend fun deleteReferralResult(adjudicationNumber: Long, chargeSequence: Int) {
    webClient.delete()
      .uri(
        "/adjudications/adjudication-number/{adjudicationNumber}/charge/{chargeSequence}/result",
        adjudicationNumber,
        chargeSequence,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createAdjudicationAwards(
    adjudicationNumber: Long,
    chargeSequence: Int,
    request: CreateHearingResultAwardRequest,
  ): CreateHearingResultAwardResponses = webClient.post()
    .uri(
      "/adjudications/adjudication-number/{adjudicationNumber}/charge/{chargeSequence}/awards",
      adjudicationNumber,
      chargeSequence,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateAdjudicationAwards(
    adjudicationNumber: Long,
    chargeSequence: Int,
    request: UpdateHearingResultAwardRequest,
  ): UpdateHearingResultAwardResponses = webClient.put()
    .uri(
      "/adjudications/adjudication-number/{adjudicationNumber}/charge/{chargeSequence}/awards",
      adjudicationNumber,
      chargeSequence,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun deleteAdjudicationAwards(
    adjudicationNumber: Long,
    chargeSequence: Int,
  ): DeleteHearingResultAwardResponses = webClient.delete()
    .uri(
      "/adjudications/adjudication-number/{adjudicationNumber}/charge/{chargeSequence}/awards",
      adjudicationNumber,
      chargeSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun quashAdjudicationAwards(
    adjudicationNumber: Long,
    chargeSequence: Int,
  ) = webClient.put()
    .uri(
      "/adjudications/adjudication-number/{adjudicationNumber}/charge/{chargeSequence}/quash",
      adjudicationNumber,
      chargeSequence,
    )
    .retrieve()
    .awaitBodilessEntity()

  suspend fun unquashAdjudicationAwards(
    adjudicationNumber: Long,
    chargeSequence: Int,
    request: UnquashHearingResultAwardRequest,
  ): UpdateHearingResultAwardResponses = webClient.put()
    .uri(
      "/adjudications/adjudication-number/{adjudicationNumber}/charge/{chargeSequence}/unquash",
      adjudicationNumber,
      chargeSequence,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun upsertReferral(
    adjudicationNumber: Long,
    chargeSequence: Int,
    request: CreateHearingResultRequest,
  ) = webClient.post()
    .uri(
      "/adjudications/adjudication-number/{adjudicationNumber}/charge/{chargeSequence}/result",
      adjudicationNumber,
      chargeSequence,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getAdaAwardsSummary(bookingId: Long): AdjudicationADAAwardSummaryResponse =
    webClient.get()
      .uri("/prisoners/booking-id/{bookingId}/awards/ada/summary", bookingId)
      .retrieve()
      .awaitBody()

  // ////////// NON-ASSOCIATIONS //////////////

  suspend fun createNonAssociation(request: CreateNonAssociationRequest): CreateNonAssociationResponse =
    webClient.post()
      .uri("/non-associations")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun amendNonAssociation(
    offenderNo1: String,
    offenderNo2: String,
    sequence: Int,
    request: UpdateNonAssociationRequest,
  ) {
    webClient.put()
      .uri(
        "/non-associations/offender/{offenderNo}/ns-offender/{nsOffenderNo}/sequence/{sequence}",
        offenderNo1,
        offenderNo2,
        sequence,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun closeNonAssociation(offenderNo1: String, offenderNo2: String, sequence: Int) {
    webClient.put()
      .uri(
        "/non-associations/offender/{offenderNo}/ns-offender/{nsOffenderNo}/sequence/{sequence}/close",
        offenderNo1,
        offenderNo2,
        sequence,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteNonAssociation(offenderNo1: String, offenderNo2: String, sequence: Int) {
    webClient.delete()
      .uri(
        "/non-associations/offender/{offenderNo}/ns-offender/{nsOffenderNo}/sequence/{sequence}",
        offenderNo1,
        offenderNo2,
        sequence,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getNonAssociations(
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<NonAssociationIdResponse> =
    webClient
      .get()
      .uri {
        it.path("/non-associations/ids")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<NonAssociationIdResponse>>())
      .awaitSingle()

  suspend fun getNonAssociationDetails(
    offender1: String,
    offender2: String,
  ): List<NonAssociationResponse> =
    webClient
      .get()
      .uri(
        "/non-associations/offender/{offenderNo}/ns-offender/{nsOffenderNo}/all",
        offender1,
        offender2,
      )
      .retrieve()
      .awaitBody()

  // ///////////////////// LOCATIONS /////////////////////////

  suspend fun createLocation(request: CreateLocationRequest): LocationIdResponse =
    webClient.post()
      .uri("/locations")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateLocation(locationId: Long, request: UpdateLocationRequest) =
    webClient.put()
      .uri("/locations/{id}", locationId)
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun deactivateLocation(locationId: Long, request: DeactivateRequest) =
    webClient.put()
      .uri("/locations/{id}/deactivate", locationId)
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun reactivateLocation(locationId: Long) =
    webClient.put()
      .uri("/locations/{id}/reactivate", locationId)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun updateLocationCapacity(locationId: Long, request: UpdateCapacityRequest) =
    webClient.put()
      .uri("/locations/{id}/capacity", locationId)
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun updateLocationCertification(locationId: Long, request: UpdateCertificationRequest) =
    webClient.put()
      .uri("/locations/{id}/certification", locationId)
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun getLocations(pageNumber: Long, pageSize: Long): PageImpl<LocationIdResponse> =
    webClient
      .get()
      .uri {
        it.path("/locations/ids")
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<LocationIdResponse>>())
      .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/locations/ids")))
      .awaitSingle()

  suspend fun getLocationDetails(id: Long): LocationResponse =
    webClient
      .get()
      .uri("/locations/{id}", id)
      .retrieve()
      .bodyToMono(LocationResponse::class.java)
      .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/locations/{id}")))
      .awaitSingle()

  // ///////////////////// COURT SENTENCING /////////////////////////

  suspend fun createCourtCase(offenderNo: String, request: CreateCourtCaseRequest): CreateCourtCaseResponse =
    webClient.post()
      .uri("/prisoners/{offenderNo}/sentencing/court-cases", offenderNo)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun createCourtAppearance(offenderNo: String, nomisCourtCaseId: Long, request: CourtAppearanceRequest): CreateCourtAppearanceResponse =
    webClient.post()
      .uri("/prisoners/{offenderNo}/sentencing/court-cases/{courtCaseId}/court-appearances", offenderNo, nomisCourtCaseId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun updateCourtAppearance(offenderNo: String, nomisCourtCaseId: Long, nomisCourtAppearanceId: Long, request: CourtAppearanceRequest): UpdateCourtAppearanceResponse =
    webClient.put()
      .uri("/prisoners/{offenderNo}/sentencing/court-cases/{nomisCourtCaseId}/court-appearances/{nomisCourtAppearanceId}", offenderNo, nomisCourtCaseId, nomisCourtAppearanceId)
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun mergesSinceDate(offenderNo: String, fromDate: LocalDate): List<MergeDetail> =
    webClient.get()
      .uri("/prisoners/{offenderNo}/merges?fromDate={fromDate}", offenderNo, fromDate)
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
  // in cell if null
  val internalLocationId: Long? = null,
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
  // in cell if null
  val internalLocationId: Long? = null,
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
  val sentenceSequence: Int?,
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

data class NomisIncentive(
  val iepLevel: NomisCodeDescription,
)

data class NomisCodeDescription(val code: String, val description: String)

class RestResponsePage<T>(
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
