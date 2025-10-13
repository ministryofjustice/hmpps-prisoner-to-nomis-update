package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@RestController
@Tag(name = "Incentives Update Resource")
class IncentivesDataRepairResource(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/incentives/prisoner/booking-id/{bookingId}/repair")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_PRISONER_TO_NOMIS__UPDATE__RW')")
  suspend fun repair(@PathVariable bookingId: Long) {
    incentivesApiService.getCurrentIncentive(bookingId)?.also { currentIncentive ->
      incentivesApiService.getIncentive(currentIncentive.id)
        .run {
          nomisApiService.createIncentive(
            this.bookingId,
            this.toNomisIncentive(),
          ).also { nomisIep ->
            // no need for a mapping - mappings are only used to prevent double creates
            // this is invoked by a user, so we know it's not a double create
            telemetryClient.trackEvent(
              "incentives-repair",
              mapOf(
                "nomisBookingId" to bookingId.toString(),
                "nomisIncentiveSequence" to nomisIep.sequence.toString(),
                "id" to currentIncentive.id.toString(),
                "offenderNo" to currentIncentive.prisonerNumber,
                "iep" to this.iepCode,
              ),
              null,
            )
          }
        }
    } ?: run {
      throw IllegalArgumentException("No current incentive for bookingId $bookingId")
    }
  }
}
