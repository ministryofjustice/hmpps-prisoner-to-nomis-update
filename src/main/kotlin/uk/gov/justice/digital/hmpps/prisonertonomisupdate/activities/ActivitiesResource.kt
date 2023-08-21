package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch

@RestController
class ActivitiesResource(
  private val activitiesService: ActivitiesService,
  private val allocationService: AllocationService,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val telemetryClient: TelemetryClient,
) {
  @PreAuthorize("hasRole('ROLE_NOMIS_ACTIVITIES')")
  @PostMapping("/activities/{activityScheduleId}")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Synchronises a new DPS Activity",
    description = "A manual method for synchronising a new DPS Activity to Nomis. Performs in the same way as the automated message based solution - intended for use as a workaround when things go wrong. Requires role <b>NOMIS_ACTIVITIES</b>",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "The DPS Activity was synchronised",
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
  suspend fun synchroniseCreateActivity(
    @Schema(description = "DPS Activity id", required = true) @PathVariable activityScheduleId: Long,
  ) {
    telemetryClient.trackEvent("activity-create-requested", mapOf("dpsActivityScheduleId" to activityScheduleId.toString()))
    activitiesService.createActivity(activityScheduleId)
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_ACTIVITIES')")
  @PutMapping("/activities/{activityScheduleId}")
  @Operation(
    summary = "Synchronises an existing DPS Activity",
    description = "A manual method for synchronising an existing DPS Activity to Nomis. Performs in the same way as the automated message based solution - intended for use as a workaround when things go wrong. Requires role <b>NOMIS_ACTIVITIES</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The DPS Activity was synchronised",
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
  suspend fun synchroniseUpdateActivity(
    @Schema(description = "DPS Activity id", required = true) @PathVariable activityScheduleId: Long,
  ) {
    telemetryClient.trackEvent("activity-amend-requested", mapOf("dpsActivityScheduleId" to activityScheduleId.toString()))
    activitiesService.updateActivity(activityScheduleId)
  }

  @PreAuthorize("hasRole('ROLE_NOMIS_ACTIVITIES')")
  @PutMapping("/allocations/{allocationId}")
  @Operation(
    summary = "Synchronises a DPS Allocation",
    description = "A manual method for synchronising a DPS Allocation to Nomis (whether new or not - performs an upsert). Performs in the same way as the automated message based solution - intended for use as a workaround when things go wrong. Requires role <b>NOMIS_ACTIVITIES</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The DPS Allocation was synchronised",
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
  suspend fun synchroniseUpsertAllocation(
    @Schema(description = "DPS Allocation id", required = true) @PathVariable allocationId: Long,
  ) {
    telemetryClient.trackEvent("activity-allocation-requested", mapOf("dpsAllocationId" to allocationId.toString()))
    allocationService.upsertAllocation(allocationId)
  }

  /**
   * For dev environment only - delete all activities and courses, for when activities environment is reset
   */
  @Hidden
  @PreAuthorize("hasRole('ROLE_QUEUE_ADMIN')")
  @DeleteMapping("/activities")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun deleteAllActivities() {
    if (eventFeatureSwitch.isEnabled("DELETEALL")) {
      activitiesService.deleteAllActivities()
    } else {
      throw RuntimeException("Attempt to delete activities in wrong environment")
    }
  }
}
