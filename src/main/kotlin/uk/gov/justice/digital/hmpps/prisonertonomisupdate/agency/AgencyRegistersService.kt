package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyEmailAddressRequest

@Service
class AgencyRegistersService(
  private val telemetryClient: TelemetryClient,
  private val dpsService: AgencyRegistersDpsApiService,
  private val nomisApiService: AgencyNomisApiService,
) {

  suspend fun courtEmailInserted(event: CourtEmailEvent) {
    val dpsCourt = dpsService.getCourt(event.additionalInformation.courtId)
    val agencyEmail = dpsCourt.emailAddresses.find { it.id == event.additionalInformation.emailId }!!
    val response = nomisApiService.createAgencyEmail(event.additionalInformation.courtId, CreateAgencyEmailAddressRequest(emailAddress = agencyEmail.address!!))

    telemetryClient.trackEvent(
      "court-email-created-success",
      mapOf(
        "courtId" to event.additionalInformation.courtId,
        "dpsEmailId" to event.additionalInformation.emailId.toString(),
        "nomisEmailId" to response.id.toString(),
      ),
      null,
    )
  }
}

data class CourtEmailEvent(
  val eventType: String,
  val additionalInformation: CourtEmailAdditionalInformation,
)

data class CourtEmailAdditionalInformation(
  val courtId: String,
  val emailId: Long,
)
