package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.api.CellMovementsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.api.NOMISMigrationAndSyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.BedAssignment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.BedAssignmentNomisMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.BedAssignmentSyncResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.CellMovementReason
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.NomisBedAssignment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class BedAssignmentDpsApiService(
  @Qualifier("bedAssignmentApiWebClient")
  webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val cellMovementsApi = CellMovementsApi(webClient)
  private val syncApi = NOMISMigrationAndSyncApi(webClient)
  private val retrySpec = retryApiService.getBackoffSpec()
    .withRetryContext(Context.of("api", "BedAssignmentDpsApiService"))

  suspend fun syncBedAssignments(prisonerNumber: String, bedAssignments: List<NomisBedAssignment>): BedAssignmentSyncResponse = syncApi
    .sync(prisonerNumber, bedAssignments)
    .awaitBodyWithRetry(retrySpec)

  suspend fun migrateBedAssignments(prisonerNumber: String, bedAssignments: List<NomisBedAssignment>): List<BedAssignmentNomisMapping> = syncApi
    .migrate(prisonerNumber, bedAssignments)
    .awaitBodyWithRetry(retrySpec)

  suspend fun getCellMovementReasonOrNull(bookingId: Long, bedAssignmentSequence: Int): CellMovementReason? = cellMovementsApi
    .getCellMovementReason(bookingId, bedAssignmentSequence)
    .awaitBodyOrNullForNotFound(retrySpec)

  suspend fun getBedAssignments(prisonerNumber: String): List<BedAssignment> = syncApi
    .findAll(prisonerNumber)
    .awaitBodyWithRetry(retrySpec)
}
