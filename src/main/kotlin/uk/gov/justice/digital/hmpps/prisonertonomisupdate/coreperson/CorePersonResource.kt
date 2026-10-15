package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@Tag(name = "Core Person Update Resource")
class CorePersonResource(
  private val reconciliationService: CorePersonReconciliationService,
) {

  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/core-person/reconciliation/{prisonNumber}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run the reconciliation for this prison number",
    description = """Retrieves the differences for a prisoner. Empty response returned if no differences found. 
      Requires PRISONER_TO_NOMIS__UPDATE__RW""",
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
        description = "Forbidden to access this endpoint. Requires PRISONER_TO_NOMIS__UPDATE__RW",
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
  suspend fun generateCorePersonReconciliationReportForPrisoner(
    @Schema(description = "Prison number aka noms id / offender id display", example = "A1234BC")
    @PathVariable prisonNumber: String,
  ) = reconciliationService.checkCorePersonMatch(prisonNumber, suppressEvents = true)

  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @PostMapping("/core-person/reconciliation")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Run a core person reconciliation",
    description = """Run a core person reconciliation for all or all active prisoners.
      Requires PRISONER_TO_NOMIS__UPDATE__RW""",
    responses = [
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
        description = "Forbidden to access this endpoint. Requires PRISONER_TO_NOMIS__UPDATE__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun reconciliation(
    @RequestParam
    @Schema(description = "reconciliation only the active prisoners or all prisoners", required = true)activeOnly: Boolean,
  ) = reconciliationService.generateReconciliationReport(activeOnly = activeOnly)
}
