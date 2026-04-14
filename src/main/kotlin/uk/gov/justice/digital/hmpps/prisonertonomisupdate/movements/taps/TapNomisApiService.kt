package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.MovementsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.TapApplicationResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.TapScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTemporaryAbsences
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapApplication
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class TapNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "ExternalMovementsNomisApiService"),
  )

  private val movementsApi = MovementsResourceApi(webClient)
  private val applicationApi = TapApplicationResourceApi(webClient)
  private val scheduleApi = TapScheduleResourceApi(webClient)

  suspend fun upsertTapApplication(offenderNo: String, request: UpsertTapApplication): UpsertTapApplicationResponse = applicationApi.prepare(applicationApi.upsertTapApplicationRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun upsertTapScheduleOut(offenderNo: String, request: UpsertTapScheduleOut): UpsertTapScheduleOutResponse = scheduleApi.prepare(scheduleApi.upsertTapScheduleOutRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createTemporaryAbsence(offenderNo: String, request: CreateTemporaryAbsenceRequest): CreateTemporaryAbsenceResponse = movementsApi.prepare(movementsApi.createTemporaryAbsenceRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createTemporaryAbsenceReturn(offenderNo: String, request: CreateTemporaryAbsenceReturnRequest): CreateTemporaryAbsenceReturnResponse = movementsApi.prepare(movementsApi.createTemporaryAbsenceReturnRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun getTemporaryAbsenceSummary(offenderNo: String): OffenderTemporaryAbsenceSummaryResponse = movementsApi.prepare(movementsApi.getTemporaryAbsencesAndMovementCountsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getTemporaryAbsenceIds(offenderNo: String): OffenderTemporaryAbsenceIdsResponse = movementsApi.getTemporaryAbsencesAndMovementIds(offenderNo)
    .awaitSingle()

  suspend fun getBookingTemporaryAbsences(bookingId: Long): BookingTemporaryAbsences? = movementsApi.prepare(movementsApi.getTemporaryAbsencesAndMovementsForBookingRequestConfig(bookingId))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getTemporaryAbsencesOrNull(offenderNo: String): OffenderTemporaryAbsencesResponse? = movementsApi.prepare(movementsApi.getTemporaryAbsencesAndMovementsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun deleteTapScheduleOut(offenderNo: String, eventId: Long) = scheduleApi.prepare(scheduleApi.deleteTapScheduleOutRequestConfig(offenderNo, eventId))
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()
}
