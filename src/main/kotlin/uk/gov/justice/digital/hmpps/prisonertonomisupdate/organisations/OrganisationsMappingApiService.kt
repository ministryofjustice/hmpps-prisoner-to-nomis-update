package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto

@Service
class OrganisationsMappingApiService(@Qualifier("mappingWebClient") val webClient: WebClient) {
  suspend fun getByDpsOrganisationIdOrNull(organisationId: String): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/organisation/dps-organisation-id/{organisationId}",
      organisationId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun deleteByDpsOrganisationId(dpsOrganisationId: String) {
    webClient.delete()
      .uri("/mapping/corporate/organisation/dps-organisation-id/{dpsOrganisationId}", dpsOrganisationId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsAddressIdOrNull(dpsAddressId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/address/dps-address-id/{dpsAddressId}",
      dpsAddressId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsAddressId(dpsAddressId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/address/dps-address-id/{dpsAddressId}",
      dpsAddressId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createAddressMapping(mappings: OrganisationsMappingDto) = webClient.post()
    .uri("/mapping/corporate/address")
    .bodyValue(mappings)
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteByNomisAddressId(nomisAddressId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/address/nomis-address-id/{nomisAddressId}",
        nomisAddressId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsPhoneIdOrNull(dpsPhoneId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/phone/dps-phone-id/{dpsPhoneId}",
      dpsPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsPhoneId(dpsPhoneId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/phone/dps-phone-id/{dpsPhoneId}",
      dpsPhoneId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createPhoneMapping(mappings: OrganisationsMappingDto) = webClient.post()
    .uri("/mapping/corporate/phone")
    .bodyValue(mappings)
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteByNomisPhoneId(nomisPhoneId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/phone/nomis-phone-id/{nomisPhoneId}",
        nomisPhoneId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsEmailIdOrNull(dpsEmailId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/email/dps-email-id/{dpsEmailId}",
      dpsEmailId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsEmailId(dpsEmailId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/email/dps-email-id/{dpsEmailId}",
      dpsEmailId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createEmailMapping(mappings: OrganisationsMappingDto) = webClient.post()
    .uri("/mapping/corporate/email")
    .bodyValue(mappings)
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteByNomisEmailId(nomisEmailId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/email/nomis-internet-address-id/{nomisEmailId}",
        nomisEmailId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsWebIdOrNull(dpsWebId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/web/dps-web-id/{dpsWebId}",
      dpsWebId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsWebId(dpsWebId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/web/dps-web-id/{dpsWebId}",
      dpsWebId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createWebMapping(mappings: OrganisationsMappingDto) = webClient.post()
    .uri("/mapping/corporate/web")
    .bodyValue(mappings)
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteByNomisWebId(nomisWebId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/web/nomis-internet-address-id/{nomisWebId}",
        nomisWebId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsAddressPhoneIdOrNull(dpsAddressPhoneId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/address-phone/dps-address-phone-id/{dpsAddressPhoneId}",
      dpsAddressPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getByDpsAddressPhoneId(dpsAddressPhoneId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/address-phone/dps-address-phone-id/{dpsAddressPhoneId}",
      dpsAddressPhoneId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createAddressPhoneMapping(mappings: OrganisationsMappingDto) = webClient.post()
    .uri("/mapping/corporate/address-phone")
    .bodyValue(mappings)
    .retrieve()
    .awaitBodilessEntityOrThrowOnConflict()

  suspend fun deleteByNomisAddressPhoneId(nomisAddressPhoneId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/address-phone/nomis-phone-id/{nomisAddressPhoneId}",
        nomisAddressPhoneId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }
}
