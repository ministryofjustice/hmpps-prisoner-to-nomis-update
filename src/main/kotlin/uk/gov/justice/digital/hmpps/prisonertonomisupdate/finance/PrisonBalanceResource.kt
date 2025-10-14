package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

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
class PrisonBalanceResource(private val reconciliationService: PrisonBalanceReconciliationService) {

  @PreAuthorize("hasRole('NOMIS_UPDATE__RECONCILIATION__R')")
  @GetMapping("/prison-balance/reconciliation/{prisonId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run the prison balance reconciliation for this prison id",
    description = """Retrieves the account balance differences for a prison. Empty response returned if no differences found. 
      Requires NOMIS_UPDATE__RECONCILIATION__R""",
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
        description = "Forbidden to access this endpoint. Requires NOMIS_UPDATE__RECONCILIATION__R",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun generateReconciliationReportForPrison(
    @Schema(description = "Prison Id", example = "MDI")
    @PathVariable prisonId: String,
  ): MismatchPrisonBalance? = reconciliationService.checkPrisonBalanceMatch(prisonId)
}
