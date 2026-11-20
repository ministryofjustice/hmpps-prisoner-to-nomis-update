package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.BedAssignmentHistoryResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class BedAssignmentNomisApiService(
  @Qualifier("nomisApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val api = BedAssignmentHistoryResourceApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "BedAssignmentNomisApiService"),
  )

  suspend fun getBedAssignmentsForPrisoner(offenderNo: String): PrisonerBedAssignmentHistoryResponse = api
    .getBedAssignmentsForPrisoner(offenderNo)
    .awaitBodyWithRetry(retrySpec)

  suspend fun getBedAssignmentOrNull(bookingId: Long, sequence: Int): BedAssignmentHistoryResponse? = api
    .getBedAssignment(bookingId, sequence)
    .awaitBodyOrNullForNotFound(retrySpec)
}
