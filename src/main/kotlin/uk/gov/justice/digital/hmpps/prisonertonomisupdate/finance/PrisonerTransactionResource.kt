package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@Tag(name = "Finance Transaction Resource")
@PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
@RequestMapping("/prisoner-transactions/reconciliation")
class PrisonerTransactionResource(private val reconciliationService: PrisonerTransactionReconciliationService) {
  @GetMapping("/transaction/{transactionId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run the prisoner transaction reconciliation for this transaction id",
    description = """Retrieves the prisoner transaction differences for a transaction. 
      Empty response returned if no differences found.
      Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW""",
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
        description = "Forbidden to access this endpoint. Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun generateReconciliationReportForPrisonerTransaction(
    @Schema(description = "Transaction Id", example = "12345", required = true)
    @PathVariable transactionId: Long,
  ) = reconciliationService.checkTransactionMatch(transactionId)
}
