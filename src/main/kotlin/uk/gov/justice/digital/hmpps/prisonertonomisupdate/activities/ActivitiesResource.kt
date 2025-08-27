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
  private val attendanceService: AttendanceService,
  private val schedulesService: SchedulesService,
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

  @PreAuthorize("hasRole('ROLE_NOMIS_ACTIVITIES')")
  @PutMapping("/attendances/{attendanceId}")
  @Operation(
    summary = "Synchronises a DPS Attendance",
    description = "A manual method for synchronising a DPS Attendance to Nomis (whether new or not - performs an upsert). Performs in the same way as the automated message based solution - intended for use as a workaround when things go wrong. Requires role <b>NOMIS_ACTIVITIES</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The DPS Attendance was synchronised",
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
  suspend fun synchroniseUpsertAttendance(
    @Schema(description = "DPS Attendance id", required = true) @PathVariable attendanceId: Long,
  ) {
    telemetryClient.trackEvent("activity-attendance-requested", mapOf("dpsAttendanceId" to attendanceId.toString()))
    attendanceService.upsertAttendance(attendanceId)
  }

  /*
   * There is a problem in preprod where the activity mappings are refreshed from prod later than NOMIS is refreshed.
   * This results in mappings existing for course schedules that don't actually exist in NOMIS preprod.
   * New course schedules are then prevented from being synchronised to NOMIS because the mapping already exists.
   *
   * When run after a preprod refresh of the mappings DB, this endpoint will remove any mappings that don't exist in NOMIS.
   */
  @PreAuthorize("hasRole('ROLE_NOMIS_UPDATE__QUEUE_ADMIN__RW')")
  @DeleteMapping("/activities/mappings/unknown-mappings")
  suspend fun deleteUnknownActivityMappings() = schedulesService.deleteUnknownMappings()

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
