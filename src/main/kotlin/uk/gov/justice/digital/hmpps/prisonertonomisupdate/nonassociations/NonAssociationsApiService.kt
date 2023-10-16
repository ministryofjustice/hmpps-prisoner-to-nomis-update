package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.LegacyNonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.NonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference

@Service
class NonAssociationsApiService(private val nonAssociationsApiWebClient: WebClient) {

  suspend fun getNonAssociation(id: Long): LegacyNonAssociation =
    nonAssociationsApiWebClient.get()
      .uri("/legacy/api/non-associations/{id}", id)
      .retrieve()
      .awaitBody()

  suspend fun getNonAssociationsBetween(offenderNo1: String, offenderNo2: String): List<NonAssociation> =
    nonAssociationsApiWebClient.post()
      .uri("/non-associations/between?includeClosed=true")
      .bodyValue(listOf(offenderNo1, offenderNo2))
      .retrieve()
      .awaitBody()

  suspend fun getAllNonAssociations(
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<NonAssociation> =
    nonAssociationsApiWebClient
      .get()
      .uri {
        it.path("/non-associations")
          .queryParam("includeClosed", true)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<NonAssociation>>())
      .awaitSingle()
}
