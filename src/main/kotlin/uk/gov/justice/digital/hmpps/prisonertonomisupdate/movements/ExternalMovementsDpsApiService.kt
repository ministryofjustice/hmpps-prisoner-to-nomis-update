package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.*
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapisyncTapAuthorisation as TapAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapisyncTapMovement as TapMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapisyncTapOccurrence as TapOccurrence

@Service
class ExternalMovementsDpsApiService(
  @Qualifier("movementsApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "ExternalMovementsDpsApiService"),
  )

  private val syncApi = SyncApi(webClient)

  suspend fun getTapAuthorisation(id: UUID): TapAuthorisation = syncApi.findTapAuthorisationById(id)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getTapOccurrence(id: UUID): TapOccurrence = syncApi.findTapOccurrenceById(id)
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun getTapMovement(id: UUID): TapMovement = syncApi.findTapMovementById(id)
    .retryWhen(backoffSpec)
    .awaitSingle()
}
