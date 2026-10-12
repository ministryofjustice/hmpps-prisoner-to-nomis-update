package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyEmailAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyPhoneNumberRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyEmailAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumbersRequest
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

  suspend fun courtPhoneInserted(event: CourtPhoneEvent) {
    val courtId = event.additionalInformation.courtId
    val dpsPhoneId = event.additionalInformation.phoneId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
      "dpsPhoneId" to dpsPhoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("court-phone-create-ignored", telemetry)
    } else {
      track("court-phone-create", telemetry) {
        val dpsCourt = dpsService.getCourt(courtId)
        val agencyPhone = dpsCourt.phoneNumbers.find { it.id == dpsPhoneId }!!
        val response = nomisApiService.createAgencyPhone(courtId, CreateAgencyPhoneNumberRequest(number = agencyPhone.number!!, typeCode = "BUS"))
        telemetry["nomisPhoneId"] = response.id.toString()
      }
    }
  }

  suspend fun courtPhoneAmended(event: CourtPhoneEvent) = refreshCourtPhones(event, "court-phone-amended")

  suspend fun courtPhoneDeleted(event: CourtPhoneEvent) = refreshCourtPhones(event, "court-phone-deleted")

  private suspend fun refreshCourtPhones(event: CourtPhoneEvent, telemetryEvent: String) {
    val courtId = event.additionalInformation.courtId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
      "dpsPhoneId" to event.additionalInformation.phoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsCourt = dpsService.getCourt(courtId)
        val response = nomisApiService.refreshAgencyPhones(
          courtId,
          UpdateAgencyPhoneNumbersRequest(phoneNumbers = dpsCourt.phoneNumbers.map { UpdateAgencyPhoneNumber(number = it.number!!, typeCode = "BUS") }),
        )
        telemetry["nomisPhoneIds"] = response.phoneNumbers.map { it.id }.joinToString(", ")
      }
    }
  }
}

private fun SourcedEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"
