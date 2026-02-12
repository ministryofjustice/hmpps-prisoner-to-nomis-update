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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.NotFoundException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate

@RestController
@Tag(name = "Finance Transaction Resource")
@PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
@RequestMapping("/prison-transactions/reconciliation")
class PrisonTransactionResource(private val reconciliationService: PrisonTransactionReconciliationService) {

  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @GetMapping("/{prisonId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run the prison (GL) transaction reconciliation for this prison id ",
    description = """Retrieves the prison (GL) transaction differences for a prison on the specified date, or today if no date is provided.
       It does not reconcile prison (GL) transactions that form part of an offender transaction.
       An empty response returned if no differences are found.
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
      ApiResponse(
        responseCode = "404",
        description = "Either no mapping found for case or case not found for prisoner",
      ),
      ApiResponse(
        responseCode = "404",
        description = "Prison (GL) Transaction Id does not exist. Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW",
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
    @Schema(description = "Prison Id", example = "MDI", required = true)
    @PathVariable prisonId: String,
    @Schema(description = "Date for prison reconcile", example = "2026-02-19")
    @RequestParam date: LocalDate = LocalDate.now(),
  ): List<MismatchPrisonTransaction>? = reconciliationService.checkTransactionsMatch(prisonId, date)
    .takeIf { it.isNotEmpty() }

  @GetMapping("/transaction/{transactionId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Run the prison (GL) transaction reconciliation for this transaction id",
    description = """Retrieves the GL transaction differences for a transaction. Empty response returned if no differences found.
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
      ApiResponse(
        responseCode = "404",
        description = "Either no mapping found for prison or not found for transaction",
      ),
      ApiResponse(
        responseCode = "404",
        description = "Prison (GL) Transaction Id does not exist. Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun manualCheckPrisonTransaction(
    @Schema(description = "Transaction Id", example = "12345", required = true)
    @PathVariable transactionId: Long,
  ) = try {
    reconciliationService.checkTransactionMatch(transactionId)
  } catch (_: WebClientResponseException.NotFound) {
    throw NotFoundException("Transaction not found $transactionId")
  }
}
