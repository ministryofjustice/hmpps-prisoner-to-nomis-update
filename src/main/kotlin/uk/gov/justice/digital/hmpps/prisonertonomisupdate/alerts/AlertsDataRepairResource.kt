package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class AlertsDataRepairResource(
  private val alertsService: AlertsService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/prisoners/{offenderNo}/alerts/resynchronise")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_MIGRATE_ALERTS')")
  @Operation(
    summary = "Resynchronises current alerts for the given prisoner from DPS back to current booking in NOMIS",
    description = "Used when an unexpected event has happened in DPS that has resulted in the NOMIS data drifting from DPS, so emergency use only. Requires ROLE_MIGRATE_ALERTS",
  )
  suspend fun repairAlerts(
    @PathVariable offenderNo: String,
  ) {
    alertsService.resynchronisePrisonerAlerts(offenderNo)
    telemetryClient.trackEvent(
      "to-nomis-synch-alerts-resynchronisation-repair",
      mapOf(
        "offenderNo" to offenderNo,
      ),
      null,
    )
  }
}
