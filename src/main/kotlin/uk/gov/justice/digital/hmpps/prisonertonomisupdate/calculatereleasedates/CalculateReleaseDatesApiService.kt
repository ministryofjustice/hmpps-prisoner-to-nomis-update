package uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates.api.LegacySentenceCalculationControllerApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates.model.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CalculateReleaseDatesApiService(
  @Qualifier("calculateReleaseDatesApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CalculateReleaseDatesApiService"),
  )

  private val api = LegacySentenceCalculationControllerApi(webClient)

  suspend fun getSentenceCalculations(prisonerId: String): List<LegacySentenceCalculationDto> = api.getAllForPrisoner(prisonerId).retryWhen(retrySpec).awaitSingle()
}
