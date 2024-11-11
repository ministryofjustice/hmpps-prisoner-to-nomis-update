package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class ContactPersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun createPerson(request: CreatePersonRequest): CreatePersonResponse = webClient.post()
    .uri(
      "/persons",
    )
    .bodyValue(request)
    .retrieve()
    .awaitBody()
}

// TODO - use real request
data class CreatePersonRequest(
  val firstName: String,
)

// TODO - use real response
data class CreatePersonResponse(
  val personId: Long,
)
