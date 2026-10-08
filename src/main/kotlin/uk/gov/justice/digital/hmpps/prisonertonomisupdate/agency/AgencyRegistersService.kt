package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyEmailAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyEmailAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.track

@Service
class AgencyRegistersService(
  override val telemetryClient: TelemetryClient,
  private val dpsService: AgencyRegistersDpsApiService,
  private val nomisApiService: AgencyNomisApiService,
) : TelemetryEnabled {

  suspend fun courtEmailInserted(event: CourtEmailEvent) {
    val courtId = event.additionalInformation.courtId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("court-email-create-ignored", telemetry)
    } else {
      track("court-email-create", telemetry) {
        val dpsCourt = dpsService.getCourt(courtId)
        val agencyEmail = dpsCourt.emailAddresses.find { it.id == dpsEmailId }!!
        val response = nomisApiService.createAgencyEmail(courtId, CreateAgencyEmailAddressRequest(emailAddress = agencyEmail.address!!))
        telemetry["nomisEmailId"] = response.id.toString()
      }
    }
  }

  suspend fun courtEmailAmended(event: CourtEmailEvent) {
    val courtId = event.additionalInformation.courtId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("court-email-amended-ignored", telemetry)
    } else {
      track("court-email-amended", telemetry) {
        val dpsCourt = dpsService.getCourt(courtId)
        val response = nomisApiService.refreshAgencyEmails(
          courtId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsCourt.emailAddresses.map { it.address!! }),
        )
        telemetry["nomisEmailIds"] = response.emailAddresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun courtEmailDeleted(event: CourtEmailEvent) {
    val courtId = event.additionalInformation.courtId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("court-email-deleted-ignored", telemetry)
    } else {
      track("court-email-deleted", telemetry) {
        val dpsCourt = dpsService.getCourt(courtId)
        val response = nomisApiService.refreshAgencyEmails(
          courtId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsCourt.emailAddresses.map { it.address!! }),
        )
        telemetry["nomisEmailIds"] = response.emailAddresses.map { it.id }.joinToString(", ")
      }
    }
  }
}

private fun SourcedEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"
