package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Incidents Update Resource")
class IncidentsDataRepairResource(
  private val incidentsService: IncidentsService,
) {
  @PostMapping("/incidents/{incidentId}/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
  suspend fun repair(@PathVariable incidentId: Long) = incidentsService.repairIncident(incidentId)
}
