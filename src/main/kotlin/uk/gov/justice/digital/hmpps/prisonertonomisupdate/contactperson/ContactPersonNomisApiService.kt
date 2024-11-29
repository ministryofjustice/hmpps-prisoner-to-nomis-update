package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonEmailResponse
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

  suspend fun createPersonEmail(personId: Long, request: CreatePersonEmailRequest): CreatePersonEmailResponse = webClient.post()
    .uri(
      "/persons/{personId}/email",
      personId,
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
}
