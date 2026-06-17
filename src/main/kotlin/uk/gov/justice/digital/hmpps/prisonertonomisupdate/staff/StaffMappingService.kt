package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.api.StaffMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.StaffMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class StaffMappingService(
  @Qualifier("mappingWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = StaffMappingResourceApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "StaffMappingService"),
  )

  suspend fun getStaffByNomisIdOrNull(nomisStaffId: Long): StaffMappingDto? = api.getStaffMappingByNomisId(nomisId = nomisStaffId)
    .awaitBodyOrNullForNotFound(retrySpec)
}
