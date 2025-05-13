package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyWithRetry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporatePhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateWebAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateWebAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateWebAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference

@Service
class OrganisationsNomisApiService(
  @Qualifier("nomisApiWebClient") private val webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", "OrganisationsNomisApiService"),
  )

  suspend fun getCorporateOrganisation(nomisCorporateId: Long): CorporateOrganisation = webClient.get()
    .uri(
      "/corporates/{corporateId}",
      nomisCorporateId,
    )
    .retrieve()
    .awaitBodyWithRetry(backoffSpec)

  suspend fun getCorporateOrganisationIds(
    pageNumber: Long = 0,
    pageSize: Long = 1,
  ): PageImpl<CorporateOrganisationIdResponse> = webClient.get()
    .uri {
      it.path("/corporates/ids")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<CorporateOrganisationIdResponse>>())
    .retryWhen(backoffSpec)
    .awaitSingle()

  suspend fun deleteCorporateOrganisation(corporateId: Long) {
    webClient.delete()
      .uri("/corporates/{corporateId}", corporateId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCorporate(request: CreateCorporateOrganisationRequest) {
    webClient.post()
      .uri(
        "/corporates",
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun updateCorporate(corporateId: Long, request: UpdateCorporateOrganisationRequest) {
    webClient.put()
      .uri(
        "/corporates/{corporateId}",
        corporateId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deleteCorporate(corporateId: Long) {
    webClient.delete()
      .uri(
        "/corporates/{corporateId}",
        corporateId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCorporateWebAddress(corporateId: Long, request: CreateCorporateWebAddressRequest): CreateCorporateWebAddressResponse = webClient.post()
    .uri(
      "/corporates/{corporateId}/web-address",
      corporateId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
  suspend fun updateCorporateWebAddress(corporateId: Long, webAddressId: Long, request: UpdateCorporateWebAddressRequest) {
    webClient.put()
      .uri(
        "/corporates/{corporateId}/web-address/{webAddressId}",
        corporateId,
        webAddressId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deleteCorporateWebAddress(corporateId: Long, webAddressId: Long) {
    webClient.delete()
      .uri(
        "/corporates/{corporateId}/web-address/{webAddressId}",
        corporateId,
        webAddressId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCorporatePhone(corporateId: Long, request: CreateCorporatePhoneRequest): CreateCorporatePhoneResponse = webClient.post()
    .uri(
      "/corporates/{corporateId}/phone",
      corporateId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
  suspend fun updateCorporatePhone(corporateId: Long, phoneId: Long, request: UpdateCorporatePhoneRequest) {
    webClient.put()
      .uri(
        "/corporates/{corporateId}/phone/{phoneId}",
        corporateId,
        phoneId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deleteCorporatePhone(corporateId: Long, phoneId: Long) {
    webClient.delete()
      .uri(
        "/corporates/{corporateId}/phone/{phoneId}",
        corporateId,
        phoneId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCorporateEmail(corporateId: Long, request: CreateCorporateEmailRequest): CreateCorporateEmailResponse = webClient.post()
    .uri(
      "/corporates/{corporateId}/email",
      corporateId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
  suspend fun updateCorporateEmail(corporateId: Long, emailId: Long, request: UpdateCorporateEmailRequest) {
    webClient.put()
      .uri(
        "/corporates/{corporateId}/email/{emailId}",
        corporateId,
        emailId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deleteCorporateEmail(corporateId: Long, emailId: Long) {
    webClient.delete()
      .uri(
        "/corporates/{corporateId}/email/{emailId}",
        corporateId,
        emailId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCorporateAddress(corporateId: Long, request: CreateCorporateAddressRequest): CreateCorporateAddressResponse = webClient.post()
    .uri(
      "/corporates/{corporateId}/address",
      corporateId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
  suspend fun updateCorporateAddress(corporateId: Long, addressId: Long, request: UpdateCorporateAddressRequest) {
    webClient.put()
      .uri(
        "/corporates/{corporateId}/address/{addressId}",
        corporateId,
        addressId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deleteCorporateAddress(corporateId: Long, addressId: Long) {
    webClient.delete()
      .uri(
        "/corporates/{corporateId}/address/{addressId}",
        corporateId,
        addressId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCorporateAddressPhone(corporateId: Long, addressId: Long, request: CreateCorporatePhoneRequest): CreateCorporatePhoneResponse = webClient.post()
    .uri(
      "/corporates/{corporateId}/address/{addressId}/phone",
      corporateId,
      addressId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
  suspend fun updateCorporateAddressPhone(corporateId: Long, addressId: Long, phoneId: Long, request: UpdateCorporatePhoneRequest) {
    webClient.put()
      .uri(
        "/corporates/{corporateId}/address/{addressId}/phone/{phoneId}",
        corporateId,
        addressId,
        phoneId,
      )
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun deleteCorporateAddressPhone(corporateId: Long, addressId: Long, phoneId: Long) {
    webClient.delete()
      .uri(
        "/corporates/{corporateId}/address/{addressId}/phone/{phoneId}",
        corporateId,
        addressId,
        phoneId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }
}
