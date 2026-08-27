package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "CSRA Update Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
class CsraReconciliationResource(
  private val reconciliationService: CsraReconciliationService,
) {
  @GetMapping("/csra/reconciliation/{offenderNo}")
  @Operation(
    summary = "Run the CSRA reconciliation for this prisoner",
    description = """Retrieves the CSRA differences for a prisoner. A null response returned if no differences found. 
      Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW""",
    responses = [ApiResponse(responseCode = "200", description = "Reconciliation differences returned")],
  )
  suspend fun manualCheckCsra(
    @Schema(description = "Prisoner's offenderNo", example = "A3456NZ")
    @PathVariable offenderNo: String,
  ) = reconciliationService.manualCheckCsra(offenderNo)
}
