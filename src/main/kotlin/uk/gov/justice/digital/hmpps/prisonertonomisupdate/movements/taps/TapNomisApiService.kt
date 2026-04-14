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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTemporaryAbsences
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationResponse
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

  suspend fun upsertTemporaryAbsenceApplication(offenderNo: String, request: UpsertTemporaryAbsenceApplicationRequest): UpsertTemporaryAbsenceApplicationResponse = movementsApi.prepare(movementsApi.upsertTemporaryAbsenceApplicationRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun upsertScheduledTemporaryAbsence(offenderNo: String, request: UpsertScheduledTemporaryAbsenceRequest): UpsertScheduledTemporaryAbsenceResponse = movementsApi.prepare(movementsApi.upsertScheduledTemporaryAbsenceRequestConfig(offenderNo, request))
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

  suspend fun deleteScheduledTemporaryAbsence(offenderNo: String, eventId: Long) = movementsApi.prepare(movementsApi.deleteScheduledTemporaryAbsenceRequestConfig(offenderNo, eventId))
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()
}
