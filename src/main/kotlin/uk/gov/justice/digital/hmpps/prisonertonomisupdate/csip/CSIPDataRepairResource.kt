package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent

@RestController
class CSIPDataRepairResource(
  private val csipService: CSIPService,
  private val telemetryClient: TelemetryClient,
) {
  @PreAuthorize("hasRole('ROLE_NOMIS_CSIP')")
  @PostMapping("/prisoners/{prisonNumber}/csip/{dpsCsipReportId}/repair")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Resynchronises a csip report inserted from DPS to NOMIS",
    description = "Used when DPS is in the correct state but NOMIS is wrong, so emergency use only. Requires ROLE_NOMIS_CSIP",
  )
  suspend fun repairCsipReport(
    @PathVariable prisonNumber: String,
    @PathVariable dpsCsipReportId: String,
  ) {
    csipService.repairCsipReport(prisonNumber = prisonNumber, dpsCsipReportId = dpsCsipReportId)
    telemetryClient.trackEvent(
      "csip-resynchronisation-repair",
      mapOf(
        "prisonNumber" to prisonNumber,
        "dpsCSIPReportId" to dpsCsipReportId,
      ),
    )
  }
}
