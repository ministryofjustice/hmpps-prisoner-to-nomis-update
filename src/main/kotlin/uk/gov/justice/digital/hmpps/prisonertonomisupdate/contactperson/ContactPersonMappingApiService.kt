package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
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

@Service
class ContactPersonMappingApiService(@Qualifier("mappingWebClient") val webClient: WebClient) {
  suspend fun getByDpsContactIdOrNull(dpsContactId: Long): PersonMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/person/dps-contact-id/{dpsContactId}",
      dpsContactId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun createPersonMapping(mappings: PersonMappingDto) =
    webClient.post()
      .uri("/mapping/contact-person/person")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()

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

  suspend fun getByDpsContactPhoneIdOrNull(dpsContactPhoneId: Long): PersonPhoneMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/phone/dps-contact-phone-id/{dpsContactPhoneId}",
      dpsContactPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId: Long): PersonPhoneMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/phone/dps-contact-address-phone-id/{dpsContactAddressPhoneId}",
      dpsContactAddressPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsContactIdentityIdOrNull(dpsContactIdentityId: Long): PersonIdentifierMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/identifier/dps-contact-identifier-id/{dpsContactIdentityId}",
      dpsContactIdentityId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId: Long): PersonContactRestrictionMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/contact-restriction/dps-prisoner-contact-restriction-id/{dpsPrisonerContactRestrictionId}",
      dpsPrisonerContactRestrictionId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

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

// TODO replace with real DTO
data class PersonRestrictionMappingDto(
  val dpsId: String,
  val nomisId: Long,
  val mappingType: MappingType,
  val label: String? = null,
  val whenCreated: String? = null,
) {
  enum class MappingType(val value: String) {
    MIGRATED("MIGRATED"),
    DPS_CREATED("DPS_CREATED"),
    NOMIS_CREATED("NOMIS_CREATED"),
  }
}
