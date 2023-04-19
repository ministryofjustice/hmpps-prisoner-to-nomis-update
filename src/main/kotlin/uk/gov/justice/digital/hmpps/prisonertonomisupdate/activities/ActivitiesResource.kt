package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ActivitiesResource(private val activitiesService: ActivitiesService) {

  /**
   * For dev environment only - delete all activities and courses, for when activities environment is reset
   */
  @Hidden
  @PreAuthorize("hasRole('ROLE_QUEUE_ADMIN')")
  @DeleteMapping("/activities")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun deleteAllActivities() {
    activitiesService.deleteAllActivities()
  }
}
