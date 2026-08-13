package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.api.CSRAReviewApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.util.UUID

@Service
class CsraDpsApiService(
  @Qualifier("csraApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = CSRAReviewApi(webClient)

  suspend fun getCsraReview(id: UUID): CsraReviewDetail = api
    .getCsraReview(id)
    .awaitSingle()
}
