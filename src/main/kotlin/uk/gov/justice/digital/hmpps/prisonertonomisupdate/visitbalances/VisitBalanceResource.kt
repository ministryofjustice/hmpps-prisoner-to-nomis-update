package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

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
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class VisitBalanceResource(
  private val reconciliationService: VisitBalanceReconciliationService,
) {

  @PreAuthorize("hasRole('NOMIS_VISIT_BALANCE')")
  @GetMapping("/visit-balance/reconciliation/{prisonNumber}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run the reconciliation for this prison number",
    description = """Retrieves the differences for a prisoner. Empty response returned if no differences found. 
      Requires NOMIS_VISIT_BALANCE""",
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
        description = "Forbidden to access this endpoint. Requires NOMIS_VISIT_BALANCE",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Offender does not exist",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun generateReconciliationReportForPrisoner(
    @Schema(description = "Prison number aka noms id / offender id display", example = "A1234BC")
    @PathVariable prisonNumber: String,
  ) = reconciliationService.checkVisitBalanceMatch(prisonNumber)
}
