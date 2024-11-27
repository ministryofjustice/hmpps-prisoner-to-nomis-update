package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class SentencingAdjustmentsDataRepairResource(
  private val sentencingAdjustmentsService: SentencingAdjustmentsService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/prisoners/{offenderNo}/sentencing-adjustments/{adjustmentId}/repair")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_MIGRATE_SENTENCING')")
  @Operation(
    summary = "Resynchronises an adjustments for the given prisoner from DPS back to NOMIS",
    description = "Used when DPS is in the correct state but NOMIS is wrong, so emergency use only. Requires ROLE_MIGRATE_SENTENCING",
  )
  suspend fun repairAdjustments(
    @PathVariable offenderNo: String,
    @PathVariable adjustmentId: String,
    @Schema(description = "Whether status of the DPS should be propagated to NOMIS", required = false, defaultValue = "false")
    @RequestParam(name = "force-status", defaultValue = "false") forceStatus: Boolean,
  ) {
    sentencingAdjustmentsService.repairAdjustment(offenderNo = offenderNo, adjustmentId = adjustmentId, forceStatus = forceStatus)
    telemetryClient.trackEvent(
      "to-nomis-synch-adjustment-repair",
      mapOf(
        "offenderNo" to offenderNo,
        "adjustmentId" to adjustmentId,
      ),
      null,
    )
  }
}
