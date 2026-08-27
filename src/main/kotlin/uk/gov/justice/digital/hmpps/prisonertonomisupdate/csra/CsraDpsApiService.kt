package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.api.CSRAReviewApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraCurrentRating
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewHistory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.UUID

@Service
class CsraDpsApiService(
  @Qualifier("csraApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = CSRAReviewApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CsraDpsApiService"),
  )

  suspend fun getCsraReview(id: UUID): CsraReviewDetail = api
    .getCsraReview(id)
    .awaitSingle()

  suspend fun getCsraHistory(prisonerNumber: String, size: Int = 1000): CsraReviewHistory = api
    .getCsraHistory(prisonerNumber = prisonerNumber, size = size)
    .retryWhen(retrySpec)
    .awaitSingle()

  suspend fun getCsraCurrent(prisonerNumber: String): CsraCurrentRating? = api
    .getCurrentRating(prisonerNumber = prisonerNumber)
    .retryWhen(retrySpec)
    .awaitSingle()
}
