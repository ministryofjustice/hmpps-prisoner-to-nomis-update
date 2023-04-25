package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch

@RestController
class AppointmentsResource(
  private val appointmentsService: AppointmentsService,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  /**
   * For dev environment only - delete all appointments, for when activities environment is reset
   */
  @Hidden
  @PreAuthorize("hasRole('ROLE_QUEUE_ADMIN')")
  @DeleteMapping("/appointments")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun deleteAllAppointments() {
    if (eventFeatureSwitch.isEnabled("DELETEALL")) {
      appointmentsService.deleteAllAppointments()
    } else {
      throw RuntimeException("Attempt to delete appointments in wrong environment")
    }
  }
}
