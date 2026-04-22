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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.OffenderTapsResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.TapApplicationResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.TapMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.TapScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTaps
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTapMovementIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTapMovementInResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTapMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTapMovementOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTapsIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TapSummary
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

  private val applicationApi = TapApplicationResourceApi(webClient)
  private val scheduleApi = TapScheduleResourceApi(webClient)
  private val movementApi = TapMovementResourceApi(webClient)
  private val offenderApi = OffenderTapsResourceApi(webClient)

  suspend fun upsertTapApplication(offenderNo: String, request: UpsertTapApplication): UpsertTapApplicationResponse = applicationApi.prepare(applicationApi.upsertTapApplicationRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun upsertTapScheduleOut(offenderNo: String, request: UpsertTapScheduleOut): UpsertTapScheduleOutResponse = scheduleApi.prepare(scheduleApi.upsertTapScheduleOutRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteTapScheduleOut(offenderNo: String, eventId: Long) = scheduleApi.prepare(scheduleApi.deleteTapScheduleOutRequestConfig(offenderNo, eventId))
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()

  suspend fun createTapMovementOut(offenderNo: String, request: CreateTapMovementOut): CreateTapMovementOutResponse = movementApi.prepare(movementApi.createTapMovementOutRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createTapMovementIn(offenderNo: String, request: CreateTapMovementIn): CreateTapMovementInResponse = movementApi.prepare(movementApi.createTapMovementInRequestConfig(offenderNo, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun getTapCounts(offenderNo: String): TapSummary = offenderApi.prepare(offenderApi.getTapCountsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getTapIds(offenderNo: String): OffenderTapsIdsResponse = offenderApi.getTapsIds(offenderNo)
    .awaitSingle()

  suspend fun getAllBookingTaps(bookingId: Long): BookingTaps? = offenderApi.getAllBookingTaps(bookingId).awaitBodyOrNullForNotFound()

  suspend fun getAllOffenderTapsOrNull(offenderNo: String): OffenderTapsResponse? = offenderApi.getAllOffenderTaps(offenderNo).awaitBodyOrNullForNotFound()
}
