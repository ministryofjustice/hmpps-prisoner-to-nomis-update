package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.ErrorResponse

@RestController
@Tag(name = "Official Visits Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW')")
class OfficialVisitsRepairResource(
  private val officialVisitsSynchronisationService: OfficialVisitsService,
) {

  @PostMapping("/prison/{prisonId}/prisoners/{offenderNo}/official-visits/{dpsOfficialVisitId}")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a visit in NOMIS from the visit in DPS",
    description = "Used when an unexpected event has happened in DPS that has resulted in the NOMIS data drifting from DPS, so emergency use only. Requires ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit details created",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Visit mapping already exists",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun createOfficialVisitFromDps(
    @PathVariable offenderNo: String,
    @PathVariable prisonId: String,
    @PathVariable dpsOfficialVisitId: Long,
  ) = officialVisitsSynchronisationService.createVisitFromDps(
    offenderNo = offenderNo,
    prisonId = prisonId,
    dpsOfficialVisitId = dpsOfficialVisitId,
  )

  @PutMapping("/prison/{prisonId}/prisoners/{offenderNo}/official-visits/{dpsOfficialVisitId}")
  @Operation(
    summary = "Updates a visit in NOMIS from the visit in DPS, will create or update visitors as required",
    description = "Used when an unexpected event has happened in DPS that has resulted in the NOMIS data drifting from DPS, so emergency use only. Requires ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit details updated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit mapping not found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun updateOfficialVisitFromNomis(
    @PathVariable offenderNo: String,
    @PathVariable prisonId: String,
    @PathVariable dpsOfficialVisitId: Long,
  ) = officialVisitsSynchronisationService.updateVisitFromDps(
    offenderNo = offenderNo,
    prisonId = prisonId,
    dpsOfficialVisitId = dpsOfficialVisitId,
  )
}
