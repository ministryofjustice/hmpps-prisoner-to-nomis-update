package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@PreAuthorize("hasAnyRole('ROLE_NOMIS_VISITS', 'ROLE_MIGRATE_NOMIS_SYSCON')")
class VisitsDataRepairResource(
  private val visitsService: VisitsService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/prisoners/{offenderNo}/visits/resynchronise/{visitId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Resynchronises the DPS visit to NOMIS",
    description = "Used when an unexpected event has happened in DPS that has resulted in the NOMIS visit not being present, so emergency use only. Requires ROLE_MIGRATE_NOMIS_SYSCON or ROLE_NOMIS_VISITS",
  )
  suspend fun repairVisit(
    @PathVariable offenderNo: String,
    @PathVariable visitId: String,
  ) {
    visitsService.resynchronisePrisonerVisit(offenderNo = offenderNo, visitId = visitId)
    telemetryClient.trackEvent(
      "to-nomis-synch-visit-resynchronisation-repair",
      mapOf(
        "offenderNo" to offenderNo,
        "visitId" to visitId,
      ),
      null,
    )
  }
}
