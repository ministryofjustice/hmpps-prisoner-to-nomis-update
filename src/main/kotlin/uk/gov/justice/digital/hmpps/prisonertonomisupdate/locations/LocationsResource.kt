package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

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

@RestController
@Tag(name = "Locations Resource")
class LocationsResource(private val locationsService: LocationsService) {
  @PreAuthorize("hasRole('PRISONER_TO_NOMIS__UPDATE__RW')")
  @PutMapping("/locations/{dpsLocationId}/repair")
  @Operation(
    summary = "Synchronises a DPS Location TO Nomis",
    description = "A manual method for synchronising a DPS Location. In other words it repairs the location by updating or creating it in Nomis depending on whether a mapping exists. Requires role <b>PRISONER_TO_NOMIS__UPDATE__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The Location was synchronised",
      ),
      ApiResponse(
        responseCode = "404",
        description = "The DPS id was not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun repairLocation(
    @PathVariable dpsLocationId: String,
    @RequestParam(name = "include-children", defaultValue = "false") includeChildren: Boolean,
  ) = locationsService.repair(dpsLocationId, includeChildren)
}
