package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.AgencyAddressDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.LegacyAgencyAddressDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.LegacyAgencyType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyEmailAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyPhoneNumberRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyEmailAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumbersRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.track

@Service
class AgencyRegistersService(
  override val telemetryClient: TelemetryClient,
  private val dpsService: AgencyRegistersDpsApiService,
  private val nomisApiService: AgencyNomisApiService,
) : TelemetryEnabled {

  suspend fun courtInserted(event: CourtEvent) {
    val courtId = event.additionalInformation.courtId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("court-created-ignored", telemetry)
    } else {
      track("court-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val response = nomisApiService.createAgency(dpsAgency.toCreateAgencyRequest(courtId, typeCode = "CRT"))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun courtAmended(event: CourtEvent) {
    val courtId = event.additionalInformation.courtId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("court-updated-ignored", telemetry)
    } else {
      track("court-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val response = nomisApiService.updateAgency(courtId, dpsAgency.toUpdateAgencyRequest(typeCode = "CRT"))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun courtDeleted(event: CourtEvent) = deleteAgency(
    agencyId = event.additionalInformation.courtId,
    agencyIdName = "courtId",
    telemetryEvent = "court-deleted",
    sourcedEvent = event,
  )

  suspend fun courtEmailInserted(event: CourtEmailEvent) {
    val courtId = event.additionalInformation.courtId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("court-email-created-ignored", telemetry)
    } else {
      track("court-email-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val agencyEmail = requireNotNull(dpsAgency.emailAddresses.find { it.id == dpsEmailId }) { "Email $dpsEmailId not found on court $courtId in DPS" }
        val response = nomisApiService.createAgencyEmail(courtId, CreateAgencyEmailAddressRequest(emailAddress = agencyEmail.address))
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
      telemetryClient.trackEvent("court-email-updated-ignored", telemetry)
    } else {
      track("court-email-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val response = nomisApiService.refreshAgencyEmails(
          courtId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsAgency.emailAddresses.map { it.address }),
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
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val response = nomisApiService.refreshAgencyEmails(
          courtId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsAgency.emailAddresses.map { it.address }),
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
      telemetryClient.trackEvent("court-phone-created-ignored", telemetry)
    } else {
      track("court-phone-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val agencyPhone = requireNotNull(dpsAgency.phoneNumbers.find { it.id == dpsPhoneId }) { "Phone $dpsPhoneId not found on court $courtId in DPS" }
        val response = nomisApiService.createAgencyPhone(courtId, CreateAgencyPhoneNumberRequest(number = agencyPhone.number, typeCode = "BUS"))
        telemetry["nomisPhoneId"] = response.id.toString()
      }
    }
  }

  suspend fun courtPhoneAmended(event: CourtPhoneEvent) = refreshCourtPhones(event, "court-phone-updated")

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
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val response = nomisApiService.refreshAgencyPhones(
          courtId,
          UpdateAgencyPhoneNumbersRequest(phoneNumbers = dpsAgency.phoneNumbers.map { UpdateAgencyPhoneNumber(number = it.number, typeCode = "BUS") }),
        )
        telemetry["nomisPhoneIds"] = response.phoneNumbers.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun courtAddressInserted(event: CourtAddressEvent) {
    val courtId = event.additionalInformation.courtId
    val dpsAddressId = event.additionalInformation.addressId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
      "dpsAddressId" to dpsAddressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("court-address-created-ignored", telemetry)
    } else {
      track("court-address-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val agencyAddress = requireNotNull(dpsAgency.addresses.find { it.id == dpsAddressId }) { "Address $dpsAddressId not found on court $courtId in DPS" }
        val response = nomisApiService.createAgencyAddress(courtId, agencyAddress.toCreateAgencyAddressRequest())
        telemetry["nomisAddressId"] = response.id.toString()
      }
    }
  }

  suspend fun courtAddressAmended(event: CourtAddressEvent) = refreshCourtAddresses(event, "court-address-updated")

  suspend fun courtAddressDeleted(event: CourtAddressEvent) = refreshCourtAddresses(event, "court-address-deleted")

  private suspend fun refreshCourtAddresses(event: CourtAddressEvent, telemetryEvent: String) {
    val courtId = event.additionalInformation.courtId
    val telemetry = mutableMapOf(
      "courtId" to courtId,
      "dpsAddressId" to event.additionalInformation.addressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val response = nomisApiService.refreshAgencyAddresses(
          courtId,
          UpdateAgencyAddressesRequest(addresses = dpsAgency.addresses.map { it.toUpdateAgencyAddress() }),
        )
        telemetry["nomisAddressIds"] = response.addresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun policeCustodySuiteInserted(event: PoliceCustodySuiteEvent) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("police-custody-suite-created-ignored", telemetry)
    } else {
      track("police-custody-suite-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val response = nomisApiService.createAgency(dpsAgency.toCreateAgencyRequest(policeCustodySuiteId, typeCode = "POLICE"))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun policeCustodySuiteAmended(event: PoliceCustodySuiteEvent) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("police-custody-suite-updated-ignored", telemetry)
    } else {
      track("police-custody-suite-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val response = nomisApiService.updateAgency(policeCustodySuiteId, dpsAgency.toUpdateAgencyRequest(typeCode = "POLICE"))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun policeCustodySuiteDeleted(event: PoliceCustodySuiteEvent) = deleteAgency(
    agencyId = event.additionalInformation.policeCustodySuiteId,
    agencyIdName = "policeCustodySuiteId",
    telemetryEvent = "police-custody-suite-deleted",
    sourcedEvent = event,
  )

  suspend fun policeCustodySuiteEmailInserted(event: PoliceCustodySuiteEmailEvent) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("police-custody-suite-email-created-ignored", telemetry)
    } else {
      track("police-custody-suite-email-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val agencyEmail = requireNotNull(dpsAgency.emailAddresses.find { it.id == dpsEmailId }) { "Email $dpsEmailId not found on police custody suite $policeCustodySuiteId in DPS" }
        val response = nomisApiService.createAgencyEmail(policeCustodySuiteId, CreateAgencyEmailAddressRequest(emailAddress = agencyEmail.address))
        telemetry["nomisEmailId"] = response.id.toString()
      }
    }
  }

  suspend fun policeCustodySuiteEmailAmended(event: PoliceCustodySuiteEmailEvent) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("police-custody-suite-email-updated-ignored", telemetry)
    } else {
      track("police-custody-suite-email-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val response = nomisApiService.refreshAgencyEmails(
          policeCustodySuiteId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsAgency.emailAddresses.map { it.address }),
        )
        telemetry["nomisEmailIds"] = response.emailAddresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun policeCustodySuiteEmailDeleted(event: PoliceCustodySuiteEmailEvent) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("police-custody-suite-email-deleted-ignored", telemetry)
    } else {
      track("police-custody-suite-email-deleted", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val response = nomisApiService.refreshAgencyEmails(
          policeCustodySuiteId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsAgency.emailAddresses.map { it.address }),
        )
        telemetry["nomisEmailIds"] = response.emailAddresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun policeCustodySuitePhoneInserted(event: PoliceCustodySuitePhoneEvent) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val dpsPhoneId = event.additionalInformation.phoneId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
      "dpsPhoneId" to dpsPhoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("police-custody-suite-phone-created-ignored", telemetry)
    } else {
      track("police-custody-suite-phone-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val agencyPhone = requireNotNull(dpsAgency.phoneNumbers.find { it.id == dpsPhoneId }) { "Phone $dpsPhoneId not found on police custody suite $policeCustodySuiteId in DPS" }
        val response = nomisApiService.createAgencyPhone(policeCustodySuiteId, CreateAgencyPhoneNumberRequest(number = agencyPhone.number, typeCode = "BUS"))
        telemetry["nomisPhoneId"] = response.id.toString()
      }
    }
  }

  suspend fun policeCustodySuitePhoneAmended(event: PoliceCustodySuitePhoneEvent) = refreshPoliceCustodySuitePhones(event, "police-custody-suite-phone-updated")

  suspend fun policeCustodySuitePhoneDeleted(event: PoliceCustodySuitePhoneEvent) = refreshPoliceCustodySuitePhones(event, "police-custody-suite-phone-deleted")

  private suspend fun refreshPoliceCustodySuitePhones(event: PoliceCustodySuitePhoneEvent, telemetryEvent: String) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
      "dpsPhoneId" to event.additionalInformation.phoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val response = nomisApiService.refreshAgencyPhones(
          policeCustodySuiteId,
          UpdateAgencyPhoneNumbersRequest(phoneNumbers = dpsAgency.phoneNumbers.map { UpdateAgencyPhoneNumber(number = it.number, typeCode = "BUS") }),
        )
        telemetry["nomisPhoneIds"] = response.phoneNumbers.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun policeCustodySuiteAddressInserted(event: PoliceCustodySuiteAddressEvent) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val dpsAddressId = event.additionalInformation.addressId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
      "dpsAddressId" to dpsAddressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("police-custody-suite-address-created-ignored", telemetry)
    } else {
      track("police-custody-suite-address-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val agencyAddress = requireNotNull(dpsAgency.addresses.find { it.id == dpsAddressId }) { "Address $dpsAddressId not found on police custody suite $policeCustodySuiteId in DPS" }
        val response = nomisApiService.createAgencyAddress(policeCustodySuiteId, agencyAddress.toCreateAgencyAddressRequest())
        telemetry["nomisAddressId"] = response.id.toString()
      }
    }
  }

  suspend fun policeCustodySuiteAddressAmended(event: PoliceCustodySuiteAddressEvent) = refreshPoliceCustodySuiteAddresses(event, "police-custody-suite-address-updated")

  suspend fun policeCustodySuiteAddressDeleted(event: PoliceCustodySuiteAddressEvent) = refreshPoliceCustodySuiteAddresses(event, "police-custody-suite-address-deleted")

  private suspend fun refreshPoliceCustodySuiteAddresses(event: PoliceCustodySuiteAddressEvent, telemetryEvent: String) {
    val policeCustodySuiteId = event.additionalInformation.policeCustodySuiteId
    val telemetry = mutableMapOf(
      "policeCustodySuiteId" to policeCustodySuiteId,
      "dpsAddressId" to event.additionalInformation.addressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(policeCustodySuiteId)) { "Police custody suite $policeCustodySuiteId not found in DPS" }
        val response = nomisApiService.refreshAgencyAddresses(
          policeCustodySuiteId,
          UpdateAgencyAddressesRequest(addresses = dpsAgency.addresses.map { it.toUpdateAgencyAddress() }),
        )
        telemetry["nomisAddressIds"] = response.addresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun approvedPremisesInserted(event: ApprovedPremisesEvent) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("approved-premises-created-ignored", telemetry)
    } else {
      track("approved-premises-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val response = nomisApiService.createAgency(dpsAgency.toCreateAgencyRequest(approvedPremisesId, typeCode = "APPR"))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun approvedPremisesAmended(event: ApprovedPremisesEvent) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("approved-premises-updated-ignored", telemetry)
    } else {
      track("approved-premises-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val response = nomisApiService.updateAgency(approvedPremisesId, dpsAgency.toUpdateAgencyRequest(typeCode = "APPR"))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun approvedPremisesDeleted(event: ApprovedPremisesEvent) = deleteAgency(
    agencyId = event.additionalInformation.approvedPremisesId,
    agencyIdName = "approvedPremisesId",
    telemetryEvent = "approved-premises-deleted",
    sourcedEvent = event,
  )

  suspend fun approvedPremisesEmailInserted(event: ApprovedPremisesEmailEvent) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("approved-premises-email-created-ignored", telemetry)
    } else {
      track("approved-premises-email-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val agencyEmail = requireNotNull(dpsAgency.emailAddresses.find { it.id == dpsEmailId }) { "Email $dpsEmailId not found on approved premises $approvedPremisesId in DPS" }
        val response = nomisApiService.createAgencyEmail(approvedPremisesId, CreateAgencyEmailAddressRequest(emailAddress = agencyEmail.address))
        telemetry["nomisEmailId"] = response.id.toString()
      }
    }
  }

  suspend fun approvedPremisesEmailAmended(event: ApprovedPremisesEmailEvent) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("approved-premises-email-updated-ignored", telemetry)
    } else {
      track("approved-premises-email-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val response = nomisApiService.refreshAgencyEmails(
          approvedPremisesId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsAgency.emailAddresses.map { it.address }),
        )
        telemetry["nomisEmailIds"] = response.emailAddresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun approvedPremisesEmailDeleted(event: ApprovedPremisesEmailEvent) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("approved-premises-email-deleted-ignored", telemetry)
    } else {
      track("approved-premises-email-deleted", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val response = nomisApiService.refreshAgencyEmails(
          approvedPremisesId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsAgency.emailAddresses.map { it.address }),
        )
        telemetry["nomisEmailIds"] = response.emailAddresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun approvedPremisesPhoneInserted(event: ApprovedPremisesPhoneEvent) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val dpsPhoneId = event.additionalInformation.phoneId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
      "dpsPhoneId" to dpsPhoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("approved-premises-phone-created-ignored", telemetry)
    } else {
      track("approved-premises-phone-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val agencyPhone = requireNotNull(dpsAgency.phoneNumbers.find { it.id == dpsPhoneId }) { "Phone $dpsPhoneId not found on approved premises $approvedPremisesId in DPS" }
        val response = nomisApiService.createAgencyPhone(approvedPremisesId, CreateAgencyPhoneNumberRequest(number = agencyPhone.number, typeCode = "BUS"))
        telemetry["nomisPhoneId"] = response.id.toString()
      }
    }
  }

  suspend fun approvedPremisesPhoneAmended(event: ApprovedPremisesPhoneEvent) = refreshApprovedPremisesPhones(event, "approved-premises-phone-updated")

  suspend fun approvedPremisesPhoneDeleted(event: ApprovedPremisesPhoneEvent) = refreshApprovedPremisesPhones(event, "approved-premises-phone-deleted")

  private suspend fun refreshApprovedPremisesPhones(event: ApprovedPremisesPhoneEvent, telemetryEvent: String) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
      "dpsPhoneId" to event.additionalInformation.phoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val response = nomisApiService.refreshAgencyPhones(
          approvedPremisesId,
          UpdateAgencyPhoneNumbersRequest(phoneNumbers = dpsAgency.phoneNumbers.map { UpdateAgencyPhoneNumber(number = it.number, typeCode = "BUS") }),
        )
        telemetry["nomisPhoneIds"] = response.phoneNumbers.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun approvedPremisesAddressInserted(event: ApprovedPremisesAddressEvent) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val dpsAddressId = event.additionalInformation.addressId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
      "dpsAddressId" to dpsAddressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("approved-premises-address-created-ignored", telemetry)
    } else {
      track("approved-premises-address-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val agencyAddress = requireNotNull(dpsAgency.addresses.find { it.id == dpsAddressId }) { "Address $dpsAddressId not found on approved premises $approvedPremisesId in DPS" }
        val response = nomisApiService.createAgencyAddress(approvedPremisesId, agencyAddress.toCreateAgencyAddressRequest())
        telemetry["nomisAddressId"] = response.id.toString()
      }
    }
  }

  suspend fun approvedPremisesAddressAmended(event: ApprovedPremisesAddressEvent) = refreshApprovedPremisesAddresses(event, "approved-premises-address-updated")

  suspend fun approvedPremisesAddressDeleted(event: ApprovedPremisesAddressEvent) = refreshApprovedPremisesAddresses(event, "approved-premises-address-deleted")

  private suspend fun refreshApprovedPremisesAddresses(event: ApprovedPremisesAddressEvent, telemetryEvent: String) {
    val approvedPremisesId = event.additionalInformation.approvedPremisesId
    val telemetry = mutableMapOf(
      "approvedPremisesId" to approvedPremisesId,
      "dpsAddressId" to event.additionalInformation.addressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(approvedPremisesId)) { "Approved premises $approvedPremisesId not found in DPS" }
        val response = nomisApiService.refreshAgencyAddresses(
          approvedPremisesId,
          UpdateAgencyAddressesRequest(addresses = dpsAgency.addresses.map { it.toUpdateAgencyAddress() }),
        )
        telemetry["nomisAddressIds"] = response.addresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun probationOfficeInserted(event: ProbationOfficeEvent) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("probation-office-created-ignored", telemetry)
    } else {
      track("probation-office-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val response = nomisApiService.createAgency(dpsAgency.toCreateAgencyRequest(probationOfficeId, typeCode = "COMM"))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun probationOfficeAmended(event: ProbationOfficeEvent) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("probation-office-updated-ignored", telemetry)
    } else {
      track("probation-office-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val response = nomisApiService.updateAgency(probationOfficeId, dpsAgency.toUpdateAgencyRequest(typeCode = "COMM"))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun probationOfficeDeleted(event: ProbationOfficeEvent) = deleteAgency(
    agencyId = event.additionalInformation.probationOfficeId,
    agencyIdName = "probationOfficeId",
    telemetryEvent = "probation-office-deleted",
    sourcedEvent = event,
  )

  suspend fun probationOfficeEmailInserted(event: ProbationOfficeEmailEvent) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("probation-office-email-created-ignored", telemetry)
    } else {
      track("probation-office-email-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val agencyEmail = requireNotNull(dpsAgency.emailAddresses.find { it.id == dpsEmailId }) { "Email $dpsEmailId not found on probation office $probationOfficeId in DPS" }
        val response = nomisApiService.createAgencyEmail(probationOfficeId, CreateAgencyEmailAddressRequest(emailAddress = agencyEmail.address))
        telemetry["nomisEmailId"] = response.id.toString()
      }
    }
  }

  suspend fun probationOfficeEmailAmended(event: ProbationOfficeEmailEvent) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("probation-office-email-updated-ignored", telemetry)
    } else {
      track("probation-office-email-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val response = nomisApiService.refreshAgencyEmails(
          probationOfficeId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsAgency.emailAddresses.map { it.address }),
        )
        telemetry["nomisEmailIds"] = response.emailAddresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun probationOfficeEmailDeleted(event: ProbationOfficeEmailEvent) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val dpsEmailId = event.additionalInformation.emailId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
      "dpsEmailId" to dpsEmailId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("probation-office-email-deleted-ignored", telemetry)
    } else {
      track("probation-office-email-deleted", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val response = nomisApiService.refreshAgencyEmails(
          probationOfficeId,
          UpdateAgencyEmailAddressesRequest(emailAddresses = dpsAgency.emailAddresses.map { it.address }),
        )
        telemetry["nomisEmailIds"] = response.emailAddresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun probationOfficePhoneInserted(event: ProbationOfficePhoneEvent) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val dpsPhoneId = event.additionalInformation.phoneId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
      "dpsPhoneId" to dpsPhoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("probation-office-phone-created-ignored", telemetry)
    } else {
      track("probation-office-phone-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val agencyPhone = requireNotNull(dpsAgency.phoneNumbers.find { it.id == dpsPhoneId }) { "Phone $dpsPhoneId not found on probation office $probationOfficeId in DPS" }
        val response = nomisApiService.createAgencyPhone(probationOfficeId, CreateAgencyPhoneNumberRequest(number = agencyPhone.number, typeCode = "BUS"))
        telemetry["nomisPhoneId"] = response.id.toString()
      }
    }
  }

  suspend fun probationOfficePhoneAmended(event: ProbationOfficePhoneEvent) = refreshProbationOfficePhones(event, "probation-office-phone-updated")

  suspend fun probationOfficePhoneDeleted(event: ProbationOfficePhoneEvent) = refreshProbationOfficePhones(event, "probation-office-phone-deleted")

  private suspend fun refreshProbationOfficePhones(event: ProbationOfficePhoneEvent, telemetryEvent: String) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
      "dpsPhoneId" to event.additionalInformation.phoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val response = nomisApiService.refreshAgencyPhones(
          probationOfficeId,
          UpdateAgencyPhoneNumbersRequest(phoneNumbers = dpsAgency.phoneNumbers.map { UpdateAgencyPhoneNumber(number = it.number, typeCode = "BUS") }),
        )
        telemetry["nomisPhoneIds"] = response.phoneNumbers.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun probationOfficeAddressInserted(event: ProbationOfficeAddressEvent) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val dpsAddressId = event.additionalInformation.addressId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
      "dpsAddressId" to dpsAddressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("probation-office-address-created-ignored", telemetry)
    } else {
      track("probation-office-address-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val agencyAddress = requireNotNull(dpsAgency.addresses.find { it.id == dpsAddressId }) { "Address $dpsAddressId not found on probation office $probationOfficeId in DPS" }
        val response = nomisApiService.createAgencyAddress(probationOfficeId, agencyAddress.toCreateAgencyAddressRequest())
        telemetry["nomisAddressId"] = response.id.toString()
      }
    }
  }

  suspend fun probationOfficeAddressAmended(event: ProbationOfficeAddressEvent) = refreshProbationOfficeAddresses(event, "probation-office-address-updated")

  suspend fun probationOfficeAddressDeleted(event: ProbationOfficeAddressEvent) = refreshProbationOfficeAddresses(event, "probation-office-address-deleted")

  private suspend fun refreshProbationOfficeAddresses(event: ProbationOfficeAddressEvent, telemetryEvent: String) {
    val probationOfficeId = event.additionalInformation.probationOfficeId
    val telemetry = mutableMapOf(
      "probationOfficeId" to probationOfficeId,
      "dpsAddressId" to event.additionalInformation.addressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(probationOfficeId)) { "Probation office $probationOfficeId not found in DPS" }
        val response = nomisApiService.refreshAgencyAddresses(
          probationOfficeId,
          UpdateAgencyAddressesRequest(addresses = dpsAgency.addresses.map { it.toUpdateAgencyAddress() }),
        )
        telemetry["nomisAddressIds"] = response.addresses.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun hospitalInserted(event: HospitalEvent) {
    val hospitalId = event.additionalInformation.hospitalId
    val telemetry = mutableMapOf(
      "hospitalId" to hospitalId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("hospital-created-ignored", telemetry)
    } else {
      track("hospital-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(hospitalId)) { "Hospital $hospitalId not found in DPS" }
        val response = nomisApiService.createAgency(dpsAgency.toCreateAgencyRequest(hospitalId, typeCode = dpsAgency.agencyType.toHospitalTypeCode(hospitalId)))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun hospitalAmended(event: HospitalEvent) {
    val hospitalId = event.additionalInformation.hospitalId
    val telemetry = mutableMapOf(
      "hospitalId" to hospitalId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("hospital-updated-ignored", telemetry)
    } else {
      track("hospital-updated", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(hospitalId)) { "Hospital $hospitalId not found in DPS" }
        val response = nomisApiService.updateAgency(hospitalId, dpsAgency.toUpdateAgencyRequest(typeCode = dpsAgency.agencyType.toHospitalTypeCode(hospitalId)))
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

  suspend fun hospitalDeleted(event: HospitalEvent) = deleteAgency(
    agencyId = event.additionalInformation.hospitalId,
    agencyIdName = "hospitalId",
    telemetryEvent = "hospital-deleted",
    sourcedEvent = event,
  )

  private suspend fun deleteAgency(
    agencyId: String,
    agencyIdName: String,
    telemetryEvent: String,
    sourcedEvent: SourcedEvent,
  ) {
    val telemetry = mutableMapOf(agencyIdName to agencyId)
    if (!sourcedEvent.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        nomisApiService.deleteAgency(agencyId)
      }
    }
  }

  suspend fun hospitalPhoneInserted(event: HospitalPhoneEvent) {
    val hospitalId = event.additionalInformation.hospitalId
    val dpsPhoneId = event.additionalInformation.phoneId
    val telemetry = mutableMapOf(
      "hospitalId" to hospitalId,
      "dpsPhoneId" to dpsPhoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("hospital-phone-created-ignored", telemetry)
    } else {
      track("hospital-phone-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(hospitalId)) { "Hospital $hospitalId not found in DPS" }
        val agencyPhone = requireNotNull(dpsAgency.phoneNumbers.find { it.id == dpsPhoneId }) { "Phone $dpsPhoneId not found on hospital $hospitalId in DPS" }
        val response = nomisApiService.createAgencyPhone(hospitalId, CreateAgencyPhoneNumberRequest(number = agencyPhone.number, typeCode = "BUS"))
        telemetry["nomisPhoneId"] = response.id.toString()
      }
    }
  }

  suspend fun hospitalPhoneAmended(event: HospitalPhoneEvent) = refreshHospitalPhones(event, "hospital-phone-updated")

  suspend fun hospitalPhoneDeleted(event: HospitalPhoneEvent) = refreshHospitalPhones(event, "hospital-phone-deleted")

  private suspend fun refreshHospitalPhones(event: HospitalPhoneEvent, telemetryEvent: String) {
    val hospitalId = event.additionalInformation.hospitalId
    val telemetry = mutableMapOf(
      "hospitalId" to hospitalId,
      "dpsPhoneId" to event.additionalInformation.phoneId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(hospitalId)) { "Hospital $hospitalId not found in DPS" }
        val response = nomisApiService.refreshAgencyPhones(
          hospitalId,
          UpdateAgencyPhoneNumbersRequest(phoneNumbers = dpsAgency.phoneNumbers.map { UpdateAgencyPhoneNumber(number = it.number, typeCode = "BUS") }),
        )
        telemetry["nomisPhoneIds"] = response.phoneNumbers.map { it.id }.joinToString(", ")
      }
    }
  }

  suspend fun hospitalAddressInserted(event: HospitalAddressEvent) {
    val hospitalId = event.additionalInformation.hospitalId
    val dpsAddressId = event.additionalInformation.addressId
    val telemetry = mutableMapOf(
      "hospitalId" to hospitalId,
      "dpsAddressId" to dpsAddressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("hospital-address-created-ignored", telemetry)
    } else {
      track("hospital-address-created", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(hospitalId)) { "Hospital $hospitalId not found in DPS" }
        val agencyAddress = requireNotNull(dpsAgency.addresses.find { it.id == dpsAddressId }) { "Address $dpsAddressId not found on hospital $hospitalId in DPS" }
        val response = nomisApiService.createAgencyAddress(hospitalId, agencyAddress.toCreateAgencyAddressRequest())
        telemetry["nomisAddressId"] = response.id.toString()
      }
    }
  }

  suspend fun hospitalAddressAmended(event: HospitalAddressEvent) = refreshHospitalAddresses(event, "hospital-address-updated")

  suspend fun hospitalAddressDeleted(event: HospitalAddressEvent) = refreshHospitalAddresses(event, "hospital-address-deleted")

  private suspend fun refreshHospitalAddresses(event: HospitalAddressEvent, telemetryEvent: String) {
    val hospitalId = event.additionalInformation.hospitalId
    val telemetry = mutableMapOf(
      "hospitalId" to hospitalId,
      "dpsAddressId" to event.additionalInformation.addressId.toString(),
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("$telemetryEvent-ignored", telemetry)
    } else {
      track(telemetryEvent, telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(hospitalId)) { "Hospital $hospitalId not found in DPS" }
        val response = nomisApiService.refreshAgencyAddresses(
          hospitalId,
          UpdateAgencyAddressesRequest(addresses = dpsAgency.addresses.map { it.toUpdateAgencyAddress() }),
        )
        telemetry["nomisAddressIds"] = response.addresses.map { it.id }.joinToString(", ")
      }
    }
  }
}

private fun SourcedEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"

private fun LegacyAgencyType.toHospitalTypeCode(hospitalId: String): String = when (this) {
  LegacyAgencyType.HOSPITAL -> "HOSPITAL"
  LegacyAgencyType.SECURE_HOSPITAL -> "HSHOSP"
  else -> throw IllegalStateException("Unexpected agency type $this for hospital $hospitalId")
}

private fun LegacyAgencyDto.toCreateAgencyRequest(agencyId: String, typeCode: String): CreateAgencyRequest = CreateAgencyRequest(
  agencyId = agencyId,
  description = name,
  typeCode = typeCode,
  active = active,
  localAuthorityCodes = listOfNotNull(localAuthorityCode),
  longDescription = description,
  deactivationDate = inactiveDate,
  contactName = contact,
  courtTypeCode = courtTypeCode,
  disabilityAccessCode = accessibleAccess.toDisabilityAccessCode(),
  areaCode = areaCode,
  subAreaCode = subareaCode,
  // NOMIS swaps the naming around: Legacy's regionCode is NOMIS's nomsRegionCode, and Legacy's
  // geographicalAreaCode is NOMIS's regionCode
  regionCode = geographicalAreaCode,
  nomsRegionCode = regionCode,
  cjitCode = cjitCode,
  payrollRegionCode = payrollRegionCode,
)

private fun LegacyAgencyDto.toUpdateAgencyRequest(typeCode: String): UpdateAgencyRequest = UpdateAgencyRequest(
  description = name,
  active = active,
  localAuthorityCodes = listOfNotNull(localAuthorityCode),
  longDescription = description,
  deactivationDate = inactiveDate,
  contactName = contact,
  courtTypeCode = courtTypeCode,
  disabilityAccessCode = accessibleAccess.toDisabilityAccessCode(),
  areaCode = areaCode,
  subAreaCode = subareaCode,
  // NOMIS swaps the naming around: Legacy's regionCode is NOMIS's nomsRegionCode, and Legacy's
  // geographicalAreaCode is NOMIS's regionCode
  regionCode = geographicalAreaCode,
  nomsRegionCode = regionCode,
  cjitCode = cjitCode,
  payrollRegionCode = payrollRegionCode,
  typeCode = typeCode,
)

private fun LegacyAgencyDto.AccessibleAccess?.toDisabilityAccessCode(): String? = when (this) {
  // NOMIS reference data as Wheelchair Access is "WC", NOMIS data has incorrectly used WHEEL (for the one agency with this set). So prefer the correct value
  LegacyAgencyDto.AccessibleAccess.WHEELCHAIR_ACCESS -> "WC"

  LegacyAgencyDto.AccessibleAccess.ACCESSIBLE -> "Y"

  LegacyAgencyDto.AccessibleAccess.NONE -> "N"

  LegacyAgencyDto.AccessibleAccess.BY_ARRANGEMENT_ONLY -> "BA"

  null -> null
}

private fun AgencyAddressDto.toUpdateAgencyAddress(): UpdateAgencyAddress = toCreateAgencyAddressRequest().let {
  UpdateAgencyAddress(
    flat = it.flat,
    premise = it.premise,
    street = it.street,
    locality = it.locality,
    postcode = it.postcode,
    city = it.city,
    county = it.county,
    country = it.country,
  )
}

private fun LegacyAgencyAddressDto.toUpdateAgencyAddress(): UpdateAgencyAddress = toCreateAgencyAddressRequest().let {
  UpdateAgencyAddress(
    flat = it.flat,
    premise = it.premise,
    street = it.street,
    locality = it.locality,
    postcode = it.postcode,
    city = it.city,
    county = it.county,
    country = it.country,
  )
}

private fun AgencyAddressDto.toCreateAgencyAddressRequest(): CreateAgencyAddressRequest = createAgencyAddressRequest(
  addressLine1 = addressLine1,
  addressLine2 = addressLine2,
  postcode = postcode,
  town = town,
  county = county,
  country = country,
)

private fun LegacyAgencyAddressDto.toCreateAgencyAddressRequest(): CreateAgencyAddressRequest = createAgencyAddressRequest(
  addressLine1 = addressLine1,
  addressLine2 = addressLine2,
  postcode = postcode,
  town = town,
  county = county,
  country = country,
)

private fun createAgencyAddressRequest(
  addressLine1: String?,
  addressLine2: String?,
  postcode: String?,
  town: String?,
  county: String?,
  country: String?,
): CreateAgencyAddressRequest {
  val line1AddressParts = addressLine1?.split(',')?.map { it.trim() } ?: emptyList()
  var flat: String? = null
  var premise: String? = null
  var street: String? = null

  // prefer using street, then spill over to premise, then finally flat (a few agency addresses in NOMIS do indeed use flat)
  when (line1AddressParts.size) {
    0 -> {}

    1 -> street = line1AddressParts[0]

    2 -> {
      premise = line1AddressParts[0]
      street = line1AddressParts[1]
    }

    else -> {
      flat = line1AddressParts[0]
      premise = line1AddressParts[1]
      street = line1AddressParts.subList(2, line1AddressParts.size).joinToString(", ")
    }
  }
  return CreateAgencyAddressRequest(
    flat = flat,
    premise = premise,
    street = street,
    locality = addressLine2,
    postcode = postcode,
    city = town,
    county = county,
    country = country,
  )
}
