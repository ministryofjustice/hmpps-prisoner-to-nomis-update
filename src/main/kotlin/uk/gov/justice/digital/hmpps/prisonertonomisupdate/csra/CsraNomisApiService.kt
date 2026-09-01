package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.CsraResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraCreateDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraCreateResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraGetDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraUpdateDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCsrasResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CsraNomisApiService(
  @Qualifier("nomisApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = CsraResourceApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CsraNomisApiService"),
  )

  suspend fun createCsra(offenderNo: String, request: CsraCreateDto): CsraCreateResponse = api
    .createCsra(offenderNo, request)
    .awaitSingle()

  suspend fun updateCsra(bookingId: Long, sequence: Int, request: CsraUpdateDto) {
    api
      .updateCsra(bookingId, sequence, request)
      .awaitSingle()
  }

  suspend fun getCsrasForPrisoner(offenderNo: String): PrisonerCsrasResponse = api
    .getCsrasForPrisoner(offenderNo)
    .retryWhen(retrySpec)
    .awaitSingle()

  suspend fun getCurrentCsraForPrisonerOrNull(offenderNo: String): CsraGetDto? = api
    .getCurrentCsraForPrisoner(offenderNo)
    .awaitBodyOrNullForNotFound(retrySpec)
}
