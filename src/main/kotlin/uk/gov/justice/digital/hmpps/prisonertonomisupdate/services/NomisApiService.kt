package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.annotation.JsonFormat
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityAsTrueNotFoundAsFalse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.BookingsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.IncentivesResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.PrisonResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.SentencingAdjustmentResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AppointmentIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AppointmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAdjudicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateGlobalIncentiveRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingResultAwardResponses
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateHearingResultRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateIncentiveRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateIncentiveResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateKeyDateAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateNonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.DeactivateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.DeleteHearingResultAwardResponses
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.DeleteHearingResultResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Hearing
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncentiveResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MergeDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Prison
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerNosWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ReferenceCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ReorderRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UnquashHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCapacityRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCertificationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateEvidenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateEvidenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateGlobalIncentiveRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateHearingRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateHearingResultAwardRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateHearingResultAwardResponses
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateKeyDateAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateRepairsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateRepairsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateSentenceAdjustmentRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class NomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  @Value($$"${hmpps.web-client.nomis.max-retries:#{null}}") private val maxRetryAttempts: Long?,
  @Value($$"${hmpps.web-client.nomis.backoff-millis:#{null}}") private val backoffMillis: Long?,
  retryApiService: RetryApiService,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  private val backoffSpec = retryApiService.getBackoffSpec(maxRetryAttempts, backoffMillis)

  private val bookingApi = BookingsResourceApi(webClient)
  private val incentivesResourceApi = IncentivesResourceApi(webClient)
  private val sentencingAdjustmentResourceApi = SentencingAdjustmentResourceApi(webClient)
  private val prisonApi = PrisonResourceApi(webClient)

  suspend fun isAgencySwitchOnForPrisoner(serviceCode: String, prisonNumber: String) = webClient.get()
    .uri("/agency-switches/{serviceCode}/prisoner/{prisonerId}", serviceCode, prisonNumber)
    .retrieve()
    .awaitBodilessEntityAsTrueNotFoundAsFalse()

  suspend fun isAgencySwitchOnForAgency(serviceCode: String, agencyId: String) = webClient.get()
    .uri("/agency-switches/{serviceCode}/agency/{agencyId}", serviceCode, agencyId)
    .retrieve()
    .awaitBodilessEntityAsTrueNotFoundAsFalse()

  suspend fun getActivePrisons(): List<Prison> = prisonApi
    .getActivePrisons()
    .retryWhen(backoffSpec)
    .awaitSingle()

  // ////////// VISITS //////////////

  suspend fun createVisit(prisonerId: String, request: CreateVisitRequest): CreateVisitResponseDto = webClient.post()
    .uri("/prisoners/{offenderNo}/visits", prisonerId)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(CreateVisitResponseDto::class.java)
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      val errorResponse = it.getResponseBodyAs(ErrorResponse::class.java) as ErrorResponse
      throw CreateVisitDuplicateResponse(nomisVisitId = errorResponse.moreInfo!!)
    }
    .awaitSingle()

  data class CreateVisitDuplicateResponse(val nomisVisitId: String) : Exception("Duplicate visit")

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

  suspend fun createIncentive(bookingId: Long, request: CreateIncentiveRequest): CreateIncentiveResponse = incentivesResourceApi
    .createIncentive(bookingId, request).awaitSingle()

  suspend fun getCurrentIncentive(bookingId: Long): IncentiveResponse? = incentivesResourceApi.prepare(
    incentivesResourceApi.getCurrentIncentiveRequestConfig(bookingId),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  // //////////////////// APPOINTMENTS /////////////////////////

  suspend fun createAppointment(request: CreateAppointmentRequest): CreateAppointmentResponse = webClient.post()
    .uri("/appointments")
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateAppointment(nomisEventId: Long, request: UpdateAppointmentRequest): ResponseEntity<Void> = webClient.put()
    .uri("/appointments/{nomisEventId}", nomisEventId)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun cancelAppointmentIgnoreIfNotFound(nomisEventId: Long): Boolean = webClient.put()
    .uri("/appointments/{nomisEventId}/cancel", nomisEventId)
    .retrieve()
    .awaitBodilessEntityAsTrueNotFoundAsFalse()

  suspend fun uncancelAppointment(nomisEventId: Long): ResponseEntity<Void> = webClient.put()
    .uri("/appointments/{nomisEventId}/uncancel", nomisEventId)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun deleteAppointment(nomisEventId: Long): ResponseEntity<Void> = webClient.delete()
    .uri("/appointments/{nomisEventId}", nomisEventId)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getAppointmentIds(
    prisonIds: List<String>,
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Int,
    pageSize: Int,
  ): PageImpl<AppointmentIdResponse> = webClient
    .get()
    .uri {
      it.path("/appointments/ids")
        .apply {
          prisonIds.forEach { queryParam("prisonIds", it) }
          fromDate?.let { queryParam("fromDate", it) }
          toDate?.let { queryParam("toDate", it) }
        }
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<AppointmentIdResponse>>())
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/appointments/ids", "prisons", prisonIds.toString())))
    .awaitSingle()

  suspend fun getAppointment(nomisEventId: Long): AppointmentResponse = webClient
    .get()
    .uri("/appointments/{nomisEventId}", nomisEventId)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/appointments/{nomisEventId}", "nomisEventId", nomisEventId)))

  suspend fun getAppointmentsByFilter(
    bookingId: Long,
    internalLocationId: Long,
    startDateTime: LocalDateTime,
  ): List<AppointmentResponse> = webClient
    .get()
    .uri(
      "/appointments/booking/{bookingId}/location/{locationId}/start/{dateTime}",
      bookingId,
      internalLocationId,
      startDateTime,
    )
    .retrieve()
    .awaitBodyWithRetry(
      backoffSpec.withRetryContext(
        Context.of(
          "api",
          "nomis-prisoner-api",
          "path", "/appointments/{bookingId}/location/{locationId}/start/{dateTime}",
          "bookingId", bookingId,
          "locationId", internalLocationId,
          "dateTime", startDateTime,
        ),
      ),
    )

  // //////////////////// SENTENCES ////////////////////////

  suspend fun createSentenceAdjustment(
    bookingId: Long,
    sentenceSequence: Long,
    request: CreateSentenceAdjustmentRequest,
  ): CreateAdjustmentResponse = sentencingAdjustmentResourceApi
    .createSentenceAdjustment(bookingId, sentenceSequence, request)
    .awaitSingle()

  suspend fun updateSentenceAdjustment(
    adjustmentId: Long,
    request: UpdateSentenceAdjustmentRequest,
  ): Unit = sentencingAdjustmentResourceApi
    .updateSentenceAdjustment(adjustmentId, request)
    .awaitSingle()

  suspend fun createKeyDateAdjustment(
    bookingId: Long,
    request: CreateKeyDateAdjustmentRequest,
  ): CreateAdjustmentResponse = sentencingAdjustmentResourceApi
    .createKeyDateAdjustment(bookingId, request)
    .awaitSingle()

  suspend fun updateKeyDateAdjustment(
    adjustmentId: Long,
    request: UpdateKeyDateAdjustmentRequest,
  ): Unit = sentencingAdjustmentResourceApi
    .updateKeyDateAdjustment(adjustmentId, request)
    .awaitSingle()

  suspend fun deleteSentenceAdjustment(adjustmentId: Long): Unit = sentencingAdjustmentResourceApi
    .deleteSentenceAdjustment(adjustmentId)
    .awaitSingle()

  suspend fun deleteKeyDateAdjustment(adjustmentId: Long): Unit = sentencingAdjustmentResourceApi
    .deleteKeyDateAdjustment(adjustmentId)
    .awaitSingle()

  suspend fun getAdjustments(bookingId: Long): SentencingAdjustmentsResponse = sentencingAdjustmentResourceApi
    .getActiveAdjustments(bookingId, activeOnly = true)
    .awaitSingle()

  // ////////// INCENTIVE LEVELS //////////////

  suspend fun getGlobalIncentiveLevel(incentiveLevel: String): ReferenceCode? = incentivesResourceApi.prepare(
    incentivesResourceApi.getGlobalIncentiveLevelRequestConfig(incentiveLevel),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun updateGlobalIncentiveLevel(code: String, incentiveLevel: UpdateGlobalIncentiveRequest) = incentivesResourceApi
    .updateGlobalIncentiveLevel(code, incentiveLevel).awaitSingle()

  suspend fun createGlobalIncentiveLevel(incentiveLevel: CreateGlobalIncentiveRequest): ReferenceCode = incentivesResourceApi
    .createGlobalIncentiveLevel(incentiveLevel).awaitSingle()

  suspend fun globalIncentiveLevelReorder(levels: List<String>) {
    incentivesResourceApi.reorderGlobalIncentiveLevels(ReorderRequest(levels)).awaitSingle()
  }

  suspend fun getActivePrisoners(
    pageNumber: Long,
    pageSize: Long,
  ): Page<PrisonerIds> = webClient.get()
    .uri {
      it.path("/prisoners/ids/active")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<PrisonerIds>>())
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/prisoners/ids/active", "page", pageNumber)))
    .awaitSingle()

  suspend fun getAllPrisonersPaged(
    pageNumber: Long,
    pageSize: Long,
  ): Page<PrisonerId> = webClient.get()
    .uri {
      it.path("/prisoners/ids/all")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<PrisonerId>>())
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/prisoners/ids/all", "page", pageNumber)))
    .awaitSingle()

  suspend fun getAllPrisoners(
    fromId: Long,
    pageSize: Int,
  ): PrisonerNosWithLast = webClient.get()
    .uri {
      it.path("/prisoners/ids/all-from-id")
        .queryParam("offenderId", fromId)
        .queryParam("pageSize", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(PrisonerNosWithLast::class.java)
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/prisoners/ids/all-from-id", "offenderId", fromId)))
    .awaitSingle()

  suspend fun getPrisonerDetails(offenderNo: String): PrisonerDetails? = webClient.get()
    .uri("/prisoners/{offenderNo}", offenderNo)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getAllLatestBookings(
    activeOnly: Boolean,
    lastBookingId: Long,
    pageSize: Int,
  ): BookingIdsWithLast = bookingApi
    .prepare(
      bookingApi.getAllLatestBookingsFromIdRequestConfig(
        bookingId = lastBookingId,
        activeOnly = activeOnly,
        pageSize = pageSize,
      ),
    )
    .retrieve()
    .bodyToMono(BookingIdsWithLast::class.java)
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/bookings/ids/latest-from-id", "bookingId", lastBookingId)))
    .awaitSingle()

  suspend fun updatePrisonIncentiveLevel(prison: String, prisonIncentive: PrisonIncentiveLevelRequest): ResponseEntity<Void> = webClient.put()
    .uri("/incentives/prison/{prison}/code/{levelCode}", prison, prisonIncentive.levelCode)
    .bodyValue(prisonIncentive)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun createPrisonIncentiveLevel(prison: String, prisonIncentive: PrisonIncentiveLevelRequest): ResponseEntity<Void> = webClient.post()
    .uri("/incentives/prison/{prison}", prison)
    .bodyValue(prisonIncentive)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getPrisonIncentiveLevel(prison: String, code: String): PrisonIncentiveLevel? = webClient.get()
    .uri("/incentives/prison/{prison}/code/{code}", prison, code)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  // ////////// ADJUDICATIONS //////////////

  suspend fun createAdjudication(offenderNo: String, request: CreateAdjudicationRequest): AdjudicationResponse = webClient.post()
    .uri("/prisoners/{offenderNo}/adjudications", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateAdjudicationRepairs(
    adjudicationNumber: Long,
    request: UpdateRepairsRequest,
  ): UpdateRepairsResponse = webClient.put()
    .uri("/adjudications/adjudication-number/{adjudicationNumber}/repairs", adjudicationNumber)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateAdjudicationEvidence(
    adjudicationNumber: Long,
    request: UpdateEvidenceRequest,
  ): UpdateEvidenceResponse = webClient.put()
    .uri("/adjudications/adjudication-number/{adjudicationNumber}/evidence", adjudicationNumber)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createHearing(adjudicationNumber: Long, request: CreateHearingRequest): CreateHearingResponse = webClient.post()
    .uri("/adjudications/adjudication-number/{adjudicationNumber}/hearings", adjudicationNumber)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun updateHearing(adjudicationNumber: Long, hearingId: Long, request: UpdateHearingRequest): Hearing = webClient.put()
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
  ): ResponseEntity<Void> = webClient.post()
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
  ): DeleteHearingResultResponse = webClient.delete()
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
  ): ResponseEntity<Void> = webClient.put()
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
  ): ResponseEntity<Void> = webClient.post()
    .uri(
      "/adjudications/adjudication-number/{adjudicationNumber}/charge/{chargeSequence}/result",
      adjudicationNumber,
      chargeSequence,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getAdaAwardsSummary(bookingId: Long): AdjudicationADAAwardSummaryResponse = webClient.get()
    .uri("/prisoners/booking-id/{bookingId}/awards/ada/summary", bookingId)
    .retrieve()
    .awaitBody()

  // ////////// NON-ASSOCIATIONS //////////////

  suspend fun createNonAssociation(request: CreateNonAssociationRequest): CreateNonAssociationResponse = webClient.post()
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
  ): Page<NonAssociationIdResponse> = webClient
    .get()
    .uri {
      it.path("/non-associations/ids")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<NonAssociationIdResponse>>())
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/non-associations/ids", "page", pageNumber)))
    .awaitSingle()

  suspend fun getNonAssociationDetails(
    offender1: String,
    offender2: String,
  ): List<NonAssociationResponse> = webClient
    .get()
    .uri(
      "/non-associations/offender/{offenderNo}/ns-offender/{nsOffenderNo}/all",
      offender1,
      offender2,
    )
    .retrieve()
    .bodyToMono(typeReference<List<NonAssociationResponse>>())
    .retryWhen(
      backoffSpec.withRetryContext(
        Context.of(
          "api",
          "nomis-prisoner-api",
          "path",
          "/non-associations/offender/{offenderNo}/ns-offender/{nsOffenderNo}/all",
          "offender1",
          offender1,
          "offender2",
          offender2,
        ),
      ),
    )
    .awaitSingle()

  suspend fun getNonAssociationsByBooking(bookingId: Long): List<NonAssociationIdResponse> = webClient.get()
    .uri("/non-associations/booking/{bookingId}", bookingId)
    .retrieve()
    .awaitBody()

  // ///////////////////// LOCATIONS /////////////////////////

  suspend fun createLocation(request: CreateLocationRequest): LocationIdResponse = webClient.post()
    .uri("/locations")
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateLocation(locationId: Long, request: UpdateLocationRequest) = webClient.put()
    .uri("/locations/{id}", locationId)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()

  suspend fun deactivateLocation(locationId: Long, request: DeactivateRequest) = webClient.put()
    .uri("/locations/{id}/deactivate", locationId)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()

  suspend fun reactivateLocation(locationId: Long) = webClient.put()
    .uri("/locations/{id}/reactivate", locationId)
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()

  suspend fun updateLocationCapacity(locationId: Long, request: UpdateCapacityRequest, ignoreOperationalCapacity: Boolean) = webClient.put()
    .uri {
      it.path("/locations/{id}/capacity")
        .queryParam("ignoreOperationalCapacity", ignoreOperationalCapacity)
        .build(locationId)
    }
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()

  suspend fun updateLocationCertification(locationId: Long, request: UpdateCertificationRequest) = webClient.put()
    .uri("/locations/{id}/certification", locationId)
    .bodyValue(request)
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()

  suspend fun getLocations(pageNumber: Long, pageSize: Long): PageImpl<LocationIdResponse> = webClient
    .get()
    .uri {
      it.path("/locations/ids")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<LocationIdResponse>>())
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/locations/ids", "page", pageNumber)))
    .awaitSingle()

  suspend fun getLocationDetails(id: Long): LocationResponse = webClient
    .get()
    .uri("/locations/{id}", id)
    .retrieve()
    .bodyToMono(LocationResponse::class.java)
    .retryWhen(backoffSpec.withRetryContext(Context.of("api", "nomis-prisoner-api", "path", "/locations/{id}")))
    .awaitSingle()

  suspend fun mergesSinceDate(offenderNo: String, fromDate: LocalDate): List<MergeDetail> = webClient.get()
    .uri("/prisoners/{offenderNo}/merges?fromDate={fromDate}", offenderNo, fromDate)
    .retrieve()
    .awaitBody()
}

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
  val visitComment: String? = null,
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

data class CreateVisitResponseDto(
  val visitId: String,
)

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
