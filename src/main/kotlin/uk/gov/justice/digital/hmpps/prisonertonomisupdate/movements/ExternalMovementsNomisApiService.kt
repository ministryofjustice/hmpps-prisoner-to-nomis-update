package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class ExternalMovementsNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "ExternalMovementsNomisApiService"),
  )

  private val movementsApi = MovementsResourceApi(webClient)

  suspend fun upsertTemporaryAbsenceApplication(offenderNo: String, request: UpsertTemporaryAbsenceApplicationRequest): UpsertTemporaryAbsenceApplicationResponse = webClient.put()
    .uri("/movements/{offenderNo}/temporary-absences/application", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun upsertScheduledTemporaryAbsence(offenderNo: String, request: UpsertScheduledTemporaryAbsenceRequest): UpsertScheduledTemporaryAbsenceResponse = webClient.put()
    .uri("/movements/{offenderNo}/temporary-absences/scheduled-temporary-absence", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createTemporaryAbsence(offenderNo: String, request: CreateTemporaryAbsenceRequest): CreateTemporaryAbsenceResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/temporary-absence", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createTemporaryAbsenceReturn(offenderNo: String, request: CreateTemporaryAbsenceReturnRequest): CreateTemporaryAbsenceReturnResponse = webClient.post()
    .uri("/movements/{offenderNo}/temporary-absences/temporary-absence-return", offenderNo)
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun getTemporaryAbsenceSummary(offenderNo: String): OffenderTemporaryAbsenceSummaryResponse = webClient.get()
    .uri("/movements/{offenderNo}/temporary-absences/summary", offenderNo)
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getTemporaryAbsenceIds(offenderNo: String): OffenderTemporaryAbsenceIdsResponse = movementsApi.getTemporaryAbsencesAndMovementIds(offenderNo)
    .awaitSingle()

  suspend fun getBookingTemporaryAbsences(bookingId: Long): BookingTemporaryAbsences? = movementsApi.prepare(movementsApi.getTemporaryAbsencesAndMovementsForBookingRequestConfig(bookingId))
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
