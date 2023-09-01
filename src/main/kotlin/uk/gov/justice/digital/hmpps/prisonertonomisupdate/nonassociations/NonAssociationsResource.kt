package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.EventFeatureSwitch

@RestController
class NonAssociationsResource(
  private val nonAssociationsService: NonAssociationsService,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  /**
   * For dev environment only - delete all nonAssociations, for when activities environment is reset
   */
  @Hidden
  @PreAuthorize("hasRole('ROLE_QUEUE_ADMIN')")
  @DeleteMapping("/non-associations")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun deleteAllNonAssociations() {
    if (eventFeatureSwitch.isEnabled("DELETEALL")) {
      // TODO nonAssociationsService.deleteAllNonAssociations()
    } else {
      throw RuntimeException("Attempt to delete nonAssociations in wrong environment")
    }
  }
}
