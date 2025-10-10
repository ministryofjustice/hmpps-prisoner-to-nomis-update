package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
@RestController
@Tag(name = "Visit Balance Update Resource")
class VisitBalanceDataRepairResource(
  private val visitBalanceService: VisitBalanceService,
) {
  @Operation(
    summary = "Resynchronises a DPS visit balance to NOMIS",
    description = "Used when an unexpected event has happened in DPS that has resulted in the NOMIS data drifting from DPS, so emergency use only. Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW",
  )
  @PostMapping("/prisoners/{prisonNumber}/visit-balance/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun repairVisitBalance(@PathVariable prisonNumber: String) {
    visitBalanceService.synchronisePrisoner(prisonNumber, eventTypeSuffix = "repair")
  }
}
