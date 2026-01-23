package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.typeReference
import java.time.LocalDateTime

@Service
class NonAssociationMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {
  suspend fun createMapping(request: NonAssociationMappingDto) {
    webClient.post()
      .uri("/mapping/non-associations")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenNonAssociationIdOrNull(id: Long): NonAssociationMappingDto? = webClient.get()
    .uri("/mapping/non-associations/non-association-id/{id}", id)
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenNonAssociationId(id: Long): NonAssociationMappingDto = webClient.get()
    .uri("/mapping/non-associations/non-association-id/{id}", id)
    .retrieve()
    .bodyToMono<NonAssociationMappingDto>()
    .awaitSingle()

  suspend fun deleteNonAssociation(id: Long) {
    webClient.delete()
      .uri("/mapping/non-associations/non-association-id/{id}", id)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun mergeNomsNumber(from: String, to: String) {
    webClient.put()
      .uri("/mapping/non-associations/merge/from/{from}/to/{to}", from, to)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun findCommon(offenderNo1: String, offenderNo2: String): List<NonAssociationMappingDto> = webClient.get()
    .uri("/mapping/non-associations/find/common-between/{offenderNo1}/and/{offenderNo2}", offenderNo1, offenderNo2)
    .retrieve()
    .bodyToMono(typeReference<List<NonAssociationMappingDto>>())
    .awaitSingle()

  suspend fun updateList(oldOffenderNo: String, newOffenderNo: String, list: List<String>) {
    webClient.put()
      .uri("/mapping/non-associations/update-list/from/{oldOffenderNo}/to/{newOffenderNo}", oldOffenderNo, newOffenderNo)
      .bodyValue(list)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun setSequence(nonAssociationId: Long, newSequence: Int) {
    webClient.put()
      .uri("/mapping/non-associations/non-association-id/{nonAssociationId}/sequence/{newSequence}", nonAssociationId, newSequence)
      .retrieve()
      .awaitBodilessEntity()
  }
}

data class NonAssociationMappingDto(
  val nonAssociationId: Long,
  val firstOffenderNo: String,
  val secondOffenderNo: String,
  val nomisTypeSequence: Int,
  val label: String? = null,
  val mappingType: String,
  val whenCreated: LocalDateTime? = null,
)
