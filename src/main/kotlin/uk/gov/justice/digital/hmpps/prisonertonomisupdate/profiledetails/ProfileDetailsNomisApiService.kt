package uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertProfileDetailsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertProfileDetailsResponse

@Service
class ProfileDetailsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun upsertProfileDetails(offenderNo: String, profileType: String, profileCode: String?): UpsertProfileDetailsResponse = webClient.put()
    .uri("/prisoners/{offenderNo}/profile-details", offenderNo)
    .bodyValue(UpsertProfileDetailsRequest(profileType, profileCode))
    .retrieve()
    .awaitBody()
}
