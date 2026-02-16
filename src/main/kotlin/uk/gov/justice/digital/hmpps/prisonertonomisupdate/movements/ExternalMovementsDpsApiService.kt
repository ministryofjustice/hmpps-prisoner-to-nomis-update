package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapCounts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.*

@Service
class ExternalMovementsDpsApiService(
  @Qualifier("movementsApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "ExternalMovementsDpsApiService"),
  )

  private val syncApi = SyncApi(webClient)

  suspend fun getTapAuthorisation(id: UUID): SyncReadTapAuthorisation = syncApi.prepare(syncApi.findTapAuthorisationByIdRequestConfig(id))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun getTapOccurrence(id: UUID): SyncReadTapOccurrence = syncApi.prepare(syncApi.findTapOccurrenceByIdRequestConfig(id))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun getTapMovement(id: UUID): SyncReadTapMovement = syncApi.prepare(syncApi.findTapMovementByIdRequestConfig(id))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun getTapReconciliation(personIdentifier: String): PersonTapCounts = syncApi.prepare(syncApi.countTemporaryAbsencesRequestConfig(personIdentifier))
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)
}
