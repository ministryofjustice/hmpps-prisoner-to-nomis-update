package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse

@Service
class ContactPersonProfileDetailsDpsApiService(@Qualifier("personalRelationshipsApiWebClient") private val webClient: WebClient) {
  suspend fun getDomesticStatus(prisonerNumber: String): SyncPrisonerDomesticStatusResponse = webClient.get()
    .uri("/sync/{prisonerNumber}/domestic-status", prisonerNumber)
    .retrieve()
    .awaitBody()

  suspend fun getNumberOfChildren(prisonerNumber: String): SyncPrisonerNumberOfChildrenResponse = webClient.get()
    .uri("/sync/{prisonerNumber}/number-of-children", prisonerNumber)
    .retrieve()
    .awaitBody()
}
