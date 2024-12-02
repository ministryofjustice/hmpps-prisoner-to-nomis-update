package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmailMappingDto
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
}
