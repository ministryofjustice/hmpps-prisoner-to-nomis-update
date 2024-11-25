package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonResponse

@Service
class ContactPersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun createPerson(request: CreatePersonRequest): CreatePersonResponse = webClient.post()
    .uri(
      "/persons",
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createPersonContact(personId: Long, request: CreatePersonContactRequest): CreatePersonContactResponse = webClient.post()
    .uri(
      "/persons/{personId}/contact",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun createPersonAddress(personId: Long, request: CreatePersonAddressRequest): CreatePersonAddressResponse = webClient.post()
    .uri(
      "/persons/{personId}/address",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
}

// TODO: replace with real DTO
data class CreatePersonAddressRequest(
  val primaryAddress: Boolean,
  val mailAddress: Boolean,
  val typeCode: String? = null,
  val flat: String? = null,
  val premise: String? = null,
  val street: String? = null,
  val locality: String? = null,
  val postcode: String? = null,
  val cityCode: String? = null,
  val countyCode: String? = null,
  val countryCode: String? = null,
  val noFixedAddress: Boolean? = null,
  val comment: String? = null,
  val startDate: java.time.LocalDate? = null,
  val endDate: java.time.LocalDate? = null,
)

data class CreatePersonAddressResponse(
  val personAddressId: Long,

)
