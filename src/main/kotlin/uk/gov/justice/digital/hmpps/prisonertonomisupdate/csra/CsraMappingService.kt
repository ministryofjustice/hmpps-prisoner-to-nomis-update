package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.CsraMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CsraMappingService(
  @Qualifier("mappingWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = CsraMappingResourceApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "CsraMappingService"),
  )

  suspend fun create(csraMappingDto: CsraMappingDto) = api
    .createCsraMapping(csraMappingDto)
    .awaitSingle()

  suspend fun getMappingByNomisId(bookingId: Long, sequence: Int) = api
    .getCsraMappingByNomisId(bookingId, sequence)
    .awaitSingle()

  suspend fun getMappingByNomisIdOrNull(bookingId: Long, sequence: Int) = api
    .getCsraMappingByNomisId(bookingId, sequence)
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingByDpsId(dpsCsraId: String) = api
    .getCsraMappingByDpsId(dpsCsraId)
    .awaitSingle()

  suspend fun getMappingByDpsIdOrNull(dpsCsraId: String) = api
    .getCsraMappingByDpsId(dpsCsraId)
    .awaitBodyOrNullForNotFound()
}
