package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class AdjudicationsDataRepairResource(
  private val adjudicationService: AdjudicationsService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/prisons/{prisonId}/prisoners/{offenderNo}/adjudication/dps-charge-number/{chargeNumber}/punishments/repair")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('NOMIS_ADJUDICATIONS')")
  @Operation(
    summary = "Resynchronises punishments for the given adjudication from DPS back to NOMIS",
    description = "Used when a domain event adjudication.punishments.updated has gone missing, so emergency use only. Requires ROLE_NOMIS_ADJUDICATIONS",
  )
  suspend fun repairPunishments(
    @PathVariable prisonId: String,
    @PathVariable offenderNo: String,
    @PathVariable chargeNumber: String,
  ) {
    adjudicationService.updatePunishments(chargeNumber = chargeNumber, offenderNo = offenderNo, prisonId = prisonId)
    telemetryClient.trackEvent(
      "adjudication-punishment-repair",
      mapOf(
        "prisonId" to prisonId,
        "chargeNumber" to chargeNumber,
        "offenderNo" to offenderNo,
      ),
      null,
    )
  }

  @DeleteMapping("/prisons/{prisonId}/prisoners/{offenderNo}/adjudication/dps-charge-number/{chargeNumber}/hearing/dps-hearing-id/{hearingId}/result")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('NOMIS_ADJUDICATIONS')")
  @Operation(
    summary = "Resynchronises a delete hearing result for the given adjudication from DPS back to NOMIS",
    description = "Used when a domain event adjudication.hearingCompleted.deleted has gone missing, so emergency use only. Requires ROLE_NOMIS_ADJUDICATIONS",
  )
  suspend fun repairDeleteHearingResult(
    @PathVariable prisonId: String,
    @PathVariable offenderNo: String,
    @PathVariable chargeNumber: String,
    @PathVariable hearingId: String,
  ) {
    adjudicationService.deleteHearingCompleted(
      chargeNumber = chargeNumber,
      prisonId = prisonId,
      prisonerNumber = offenderNo,
      hearingId = hearingId,
    )
    telemetryClient.trackEvent(
      "adjudication-hearing-delete-result-repair",
      mapOf(
        "prisonId" to prisonId,
        "chargeNumber" to chargeNumber,
        "hearingId" to hearingId,
        "offenderNo" to offenderNo,
      ),
      null,
    )
  }
}
