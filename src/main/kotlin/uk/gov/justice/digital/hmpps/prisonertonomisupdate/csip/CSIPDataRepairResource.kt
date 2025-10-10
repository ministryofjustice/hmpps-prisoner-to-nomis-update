package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent

@RestController
@Tag(name = "CSIP Update Resource")
class CSIPDataRepairResource(
  private val csipService: CSIPService,
  private val telemetryClient: TelemetryClient,
) {
  @PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
  @PostMapping("/prisoners/{prisonNumber}/csip/{dpsCsipReportId}/repair")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Resynchronises a csip report inserted from DPS to NOMIS",
    description = "Used when DPS is in the correct state but NOMIS is wrong, so emergency use only. Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW",
  )
  suspend fun repairCsipReport(
    @PathVariable prisonNumber: String,
    @PathVariable dpsCsipReportId: String,
    @Schema(description = "Whether to clear existing child mappings", required = false, defaultValue = "false")
    @RequestParam clearChildMappings: Boolean = false,
  ) {
    csipService.repairCsipReport(prisonNumber = prisonNumber, dpsCsipReportId = dpsCsipReportId, clearChildMappings = clearChildMappings)
    telemetryClient.trackEvent(
      "csip-resynchronisation-repair",
      mapOf(
        "prisonNumber" to prisonNumber,
        "dpsCSIPReportId" to dpsCsipReportId,
      ),
    )
  }
}
