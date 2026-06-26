package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import java.util.UUID

@RestController
@Tag(name = "Court Scheduler Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
class CourtSchedulerResource(
  private val courtScheduleService: CourtSchedulerAppearanceService,
  private val telemetryClient: TelemetryClient,
) {

  @PutMapping("/court-scheduler/court/schedule/out/{prisonerNumber}/{dpsCourtAppearanceId}")
  @Operation(
    summary = "Synchronises a court schedule",
    description = "An endpoint to synchronise a court schedule. The recreate query parameter is passed onto nomis-prisoner-api to tell it to recreate using the eventId from the existing mapping. This is to recover from court schedules deleted by remand and sentencing. Requires role <b>PRISONER_TO_NOMIS__UPDATE__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The court schedule was synchronised",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to start migration",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun synchroniseCourtSchedule(
    @Schema(description = "Prisoner number", required = true) @PathVariable prisonerNumber: String,
    @Schema(description = "DPS court appearance Id", required = true) @PathVariable dpsCourtAppearanceId: UUID,
    @Schema(description = "Whether to recreate a deleted court schedule with the existing mapping eventId") @RequestParam recreate: Boolean = false,
  ) {
    val telemetry = mutableMapOf<String, String>(
      "offenderNo" to prisonerNumber,
      "dpsCourtAppearanceId" to "$dpsCourtAppearanceId",
      "recreate" to "$recreate",
    )
    telemetryClient.trackEvent("court-scheduler-schedule-sync-requested", telemetry)
    courtScheduleService.courtAppearanceChanged(prisonerNumber, dpsCourtAppearanceId, telemetry, recreate)
  }
}
