package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Appointments Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
class AppointmentsResource(
  private val appointmentsService: AppointmentsService,
) {
  @PutMapping("/appointments/repair/{appointmentAttendeeId}")
  @Operation(
    summary = "Resynchronise an appointment instance / attendee to Nomis",
    description = """Create or update a DPS appointment attendee in Nomis, depending on whether a mapping exists. 
      Requires ROLE_PRISONER_TO_NOMIS__UPDATE__RW""",
    responses = [ApiResponse(responseCode = "200", description = "Reconciliation differences returned")],
  )
  suspend fun repairAppointment(
    @Schema(description = "Appointment attendee DPS id", example = "123456789")
    @PathVariable appointmentAttendeeId: Long,
  ) = appointmentsService.repairAppointment(appointmentAttendeeId)
}
