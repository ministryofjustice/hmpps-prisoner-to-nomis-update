package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Finance Update Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
class PrisonerBalanceResource(
  private val reconciliationService: PrisonerBalanceReconciliationService,
) {
  @GetMapping("/prisoner-balance/reconciliation/id/{rootOffenderId}")
  @Operation(
    summary = "Run the prisoner balance reconciliation for this prisoner",
    description = """Retrieves the account balance differences for a prisoner. A null response returned if no differences found. 
      Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW""",
    responses = [ApiResponse(responseCode = "200", description = "Reconciliation differences returned")],
  )
  suspend fun manualCheckCaseOffenderId(
    @Schema(description = "Prisoner's rootOffenderId", example = "1234567")
    @PathVariable rootOffenderId: Long,
  ) = reconciliationService.manualCheckPrisonerBalance(rootOffenderId)

  @GetMapping("/prisoner-balance/reconciliation/{offenderNo}")
  @Operation(
    summary = "Run the prisoner balance reconciliation for this prisoner",
    description = """Retrieves the account balance differences for a prisoner. A null response returned if no differences found. 
      Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW""",
    responses = [ApiResponse(responseCode = "200", description = "Reconciliation differences returned")],
  )
  suspend fun manualCheckCaseOffenderNo(
    @Schema(description = "Prisoner's offenderNo", example = "A3456NZ")
    @PathVariable offenderNo: String,
  ) = reconciliationService.manualCheckPrisonerBalance(offenderNo)
}
