package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertPhysicalAttributesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertPhysicalAttributesResponse

@Service
class PhysAttrNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun upsertPhysicalAttributes(offenderNo: String, height: Int?, weight: Int?): UpsertPhysicalAttributesResponse =
    webClient.put()
      .uri("/prisoners/{offenderNo}/physical-attributes", offenderNo)
      .bodyValue(UpsertPhysicalAttributesRequest(height, weight))
      .retrieve()
      .awaitBody()
}
