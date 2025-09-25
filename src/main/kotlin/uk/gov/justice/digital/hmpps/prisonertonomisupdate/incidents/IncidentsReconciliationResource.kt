package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class IncidentsReconciliationResource(
  private val reconciliationService: IncidentsReconciliationService,
) {

  @PreAuthorize("hasRole('NOMIS_INCIDENTS')")
  @GetMapping("/incidents/reconciliation/{nomisIncidentId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run reconciliation against this incident",
    description = """Retrieves the differences for the incident report. Empty response returned if no differences found.
      Requires NOMIS_INCIDENTS""",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Reconciliation differences returned",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires NOMIS_INCIDENTS",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Incident does not exist",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun generateReconciliationReportForIncident(
    @Schema(description = "Nomis Incident Id", example = "123")
    @PathVariable nomisIncidentId: Long,
  ) = try {
    reconciliationService.checkIncidentMatch(nomisIncidentId)
  } catch (_: NotFound) {
    throw NotFoundException("Incident not found $nomisIncidentId")
  }
}
