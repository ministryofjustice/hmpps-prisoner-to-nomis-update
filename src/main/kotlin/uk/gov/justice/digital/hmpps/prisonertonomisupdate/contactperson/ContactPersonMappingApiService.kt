package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonRestrictionMappingDto

@Service
class ContactPersonMappingApiService(@Qualifier("mappingWebClient") val webClient: WebClient) {
  suspend fun getByDpsContactIdOrNull(dpsContactId: Long): PersonMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/person/dps-contact-id/{dpsContactId}",
      dpsContactId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsContactId(dpsContactId: Long): PersonMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/person/dps-contact-id/{dpsContactId}",
      dpsContactId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createPersonMapping(mappings: PersonMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/person")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteByDpsContactId(dpsContactId: Long) {
    webClient.delete()
      .uri("/mapping/contact-person/person/dps-contact-id/{dpsContactId}", dpsContactId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsPrisonerContactIdOrNull(dpsPrisonerContactId: Long): PersonContactMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/contact/dps-prisoner-contact-id/{dpsPrisonerContactId}",
      dpsPrisonerContactId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsPrisonerContactId(dpsPrisonerContactId: Long): PersonContactMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/contact/dps-prisoner-contact-id/{dpsPrisonerContactId}",
      dpsPrisonerContactId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createContactMapping(mappings: PersonContactMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/contact")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getByDpsContactAddressIdOrNull(dpsContactAddressId: Long): PersonAddressMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/address/dps-contact-address-id/{dpsContactAddressId}",
      dpsContactAddressId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsContactAddressId(dpsContactAddressId: Long): PersonAddressMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/address/dps-contact-address-id/{dpsContactAddressId}",
      dpsContactAddressId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisAddressId(nomisAddressId: Long) {
    webClient.delete()
      .uri(
        "/mapping/contact-person/address/nomis-address-id/{nomisAddressId}",
        nomisAddressId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsContactPhoneIdOrNull(dpsContactPhoneId: Long): PersonPhoneMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/phone/dps-contact-phone-id/{dpsContactPhoneId}",
      dpsContactPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsContactPhoneId(dpsContactPhoneId: Long): PersonPhoneMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/phone/dps-contact-phone-id/{dpsContactPhoneId}",
      dpsContactPhoneId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId: Long): PersonPhoneMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/phone/dps-contact-address-phone-id/{dpsContactAddressPhoneId}",
      dpsContactAddressPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()
  suspend fun getByDpsContactAddressPhoneId(dpsContactAddressPhoneId: Long): PersonPhoneMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/phone/dps-contact-address-phone-id/{dpsContactAddressPhoneId}",
      dpsContactAddressPhoneId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisPhoneId(nomisPhoneId: Long) {
    webClient.delete()
      .uri(
        "/mapping/contact-person/phone/nomis-phone-id/{nomisPhoneId}",
        nomisPhoneId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsContactIdentityIdOrNull(dpsContactIdentityId: Long): PersonIdentifierMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/identifier/dps-contact-identifier-id/{dpsContactIdentityId}",
      dpsContactIdentityId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsContactIdentityId(dpsContactIdentityId: Long): PersonIdentifierMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/identifier/dps-contact-identifier-id/{dpsContactIdentityId}",
      dpsContactIdentityId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisIdentifierIds(nomisPersonId: Long, nomisSequenceNumber: Long) {
    webClient.delete()
      .uri(
        "/mapping/contact-person/identifier/nomis-person-id/{nomisPersonId}/nomis-sequence-number/{nomisSequenceNumber}",
        nomisPersonId,
        nomisSequenceNumber,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId: Long): PersonContactRestrictionMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/contact-restriction/dps-prisoner-contact-restriction-id/{dpsPrisonerContactRestrictionId}",
      dpsPrisonerContactRestrictionId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsPrisonerContactRestrictionId(dpsPrisonerContactRestrictionId: Long): PersonContactRestrictionMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/contact-restriction/dps-prisoner-contact-restriction-id/{dpsPrisonerContactRestrictionId}",
      dpsPrisonerContactRestrictionId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getByDpsContactRestrictionIdOrNull(dpsContactRestrictionId: Long): PersonRestrictionMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/person-restriction/dps-contact-restriction-id/{dpsContactRestrictionId}",
      dpsContactRestrictionId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsContactRestrictionId(dpsContactRestrictionId: Long): PersonRestrictionMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/person-restriction/dps-contact-restriction-id/{dpsContactRestrictionId}",
      dpsContactRestrictionId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createAddressMapping(mappings: PersonAddressMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/address")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()

  suspend fun getByDpsContactEmailIdOrNull(dpsContactEmailId: Long): PersonEmailMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/email/dps-contact-email-id/{dpsContactEmailId}",
      dpsContactEmailId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsContactEmailId(dpsContactEmailId: Long): PersonEmailMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/email/dps-contact-email-id/{dpsContactEmailId}",
      dpsContactEmailId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisEmailId(nomisInternetAddressId: Long) {
    webClient.delete()
      .uri(
        "/mapping/contact-person/email/nomis-internet-address-id/{nomisAddressId}",
        nomisInternetAddressId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createEmailMapping(mappings: PersonEmailMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/email")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()

  suspend fun createPhoneMapping(mappings: PersonPhoneMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/phone")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()

  suspend fun createIdentifierMapping(mappings: PersonIdentifierMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/identifier")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()

  suspend fun createContactRestrictionMapping(mappings: PersonContactRestrictionMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/contact-restriction")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()

  suspend fun createPersonRestrictionMapping(mappings: PersonRestrictionMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/person-restriction")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
}
