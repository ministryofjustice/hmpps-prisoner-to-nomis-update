package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NonAssociationIdResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class NonAssociationsResource(
  private val nonAssociationsReconciliationService: NonAssociationsReconciliationService,
) {

  @PreAuthorize("hasRole('NOMIS_NON_ASSOCIATIONS')")
  @GetMapping("/non-associations/reconciliation/{prisonNumber1}/ns/{prisonNumber2}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @Operation(
    summary = "Run the reconciliation for this prison number",
    description = """Retrieves the differences for a prisoner. Empty response returned if no differences found. 
      Requires ROLE_NOMIS_NON_ASSOCIATIONS""",
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
        description = "Forbidden to access this endpoint. Requires ROLE_NOMIS_NON_ASSOCIATIONS",
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
    @Schema(description = "Prison number aka noms id / offender id display", example = "A1234BC") @PathVariable prisonNumber1: String,
    @Schema(description = "Prison number aka noms id / offender id display", example = "A1234BC") @PathVariable prisonNumber2: String,
  ) = nonAssociationsReconciliationService.checkMatchOrThrowException(NonAssociationIdResponse(prisonNumber1, prisonNumber2)).let {
    it.first.ifEmpty { null }
  }
}
