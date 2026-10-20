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
      telemetryClient.trackEvent("court-create-ignored", telemetry)
    } else {
      track("court-create", telemetry) {
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
        val response = nomisApiService.updateAgency(courtId, dpsAgency.toUpdateAgencyRequest())
        telemetry["nomisAgencyId"] = response.agencyId
      }
    }
  }

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
      telemetryClient.trackEvent("court-email-amended-ignored", telemetry)
    } else {
      track("court-email-amended", telemetry) {
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
      telemetryClient.trackEvent("court-phone-create-ignored", telemetry)
    } else {
      track("court-phone-create", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val agencyPhone = requireNotNull(dpsAgency.phoneNumbers.find { it.id == dpsPhoneId }) { "Phone $dpsPhoneId not found on court $courtId in DPS" }
        val response = nomisApiService.createAgencyPhone(courtId, CreateAgencyPhoneNumberRequest(number = agencyPhone.number, typeCode = "BUS"))
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
      telemetryClient.trackEvent("court-address-create-ignored", telemetry)
    } else {
      track("court-address-create", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(courtId)) { "Court $courtId not found in DPS" }
        val agencyAddress = requireNotNull(dpsAgency.addresses.find { it.id == dpsAddressId }) { "Address $dpsAddressId not found on court $courtId in DPS" }
        val response = nomisApiService.createAgencyAddress(courtId, agencyAddress.toCreateAgencyAddressRequest())
        telemetry["nomisAddressId"] = response.id.toString()
      }
    }
  }

  suspend fun courtAddressAmended(event: CourtAddressEvent) = refreshCourtAddresses(event, "court-address-amended")

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

  suspend fun hospitalInserted(event: HospitalEvent) {
    val hospitalId = event.additionalInformation.hospitalId
    val telemetry = mutableMapOf(
      "hospitalId" to hospitalId,
    )
    if (!event.didOriginateInDPS()) {
      telemetryClient.trackEvent("hospital-create-ignored", telemetry)
    } else {
      track("hospital-create", telemetry) {
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
        val response = nomisApiService.updateAgency(hospitalId, dpsAgency.toUpdateAgencyRequest())
        telemetry["nomisAgencyId"] = response.agencyId
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
      telemetryClient.trackEvent("hospital-phone-create-ignored", telemetry)
    } else {
      track("hospital-phone-create", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(hospitalId)) { "Hospital $hospitalId not found in DPS" }
        val agencyPhone = requireNotNull(dpsAgency.phoneNumbers.find { it.id == dpsPhoneId }) { "Phone $dpsPhoneId not found on hospital $hospitalId in DPS" }
        val response = nomisApiService.createAgencyPhone(hospitalId, CreateAgencyPhoneNumberRequest(number = agencyPhone.number, typeCode = "BUS"))
        telemetry["nomisPhoneId"] = response.id.toString()
      }
    }
  }

  suspend fun hospitalPhoneAmended(event: HospitalPhoneEvent) = refreshHospitalPhones(event, "hospital-phone-amended")

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
      telemetryClient.trackEvent("hospital-address-create-ignored", telemetry)
    } else {
      track("hospital-address-create", telemetry) {
        val dpsAgency = requireNotNull(dpsService.getAgency(hospitalId)) { "Hospital $hospitalId not found in DPS" }
        val agencyAddress = requireNotNull(dpsAgency.addresses.find { it.id == dpsAddressId }) { "Address $dpsAddressId not found on hospital $hospitalId in DPS" }
        val response = nomisApiService.createAgencyAddress(hospitalId, agencyAddress.toCreateAgencyAddressRequest())
        telemetry["nomisAddressId"] = response.id.toString()
      }
    }
  }

  suspend fun hospitalAddressAmended(event: HospitalAddressEvent) = refreshHospitalAddresses(event, "hospital-address-amended")

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

private fun LegacyAgencyDto.toUpdateAgencyRequest(): UpdateAgencyRequest = UpdateAgencyRequest(
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
)

private fun LegacyAgencyDto.AccessibleAccess?.toDisabilityAccessCode(): String? = when (this) {
  LegacyAgencyDto.AccessibleAccess.WHEELCHAIR_ACCESS -> "WHEEL"
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
