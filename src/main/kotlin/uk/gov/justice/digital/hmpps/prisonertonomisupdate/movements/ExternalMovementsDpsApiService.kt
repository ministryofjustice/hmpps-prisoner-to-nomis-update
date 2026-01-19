package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
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

  suspend fun getTapAuthorisation(id: UUID): SyncReadTapAuthorisation = syncApi.findTapAuthorisationById(id)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getTapOccurrence(id: UUID): SyncReadTapOccurrence = syncApi.findTapOccurrenceById(id)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getTapMovement(id: UUID): SyncReadTapMovement = syncApi.findTapMovementById(id)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getTapReconciliation(personIdentifier: String): PersonTapCounts = syncApi.countTemporaryAbsences(personIdentifier)
    .retryWhen(backoffSpec)
    .awaitSingle()
}
