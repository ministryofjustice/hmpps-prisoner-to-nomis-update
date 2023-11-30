package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
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

  suspend fun getMappingGivenNonAssociationIdOrNull(id: Long): NonAssociationMappingDto? =
    webClient.get()
      .uri("/mapping/non-associations/non-association-id/{id}", id)
      .retrieve()
      .awaitBodyOrNullForNotFound()

  suspend fun getMappingGivenNonAssociationId(id: Long): NonAssociationMappingDto =
    webClient.get()
      .uri("/mapping/non-associations/non-association-id/{id}", id)
      .retrieve()
      .bodyToMono(NonAssociationMappingDto::class.java)
      .awaitSingle()

  suspend fun deleteNonAssociation(id: Long) {
    webClient.delete()
      .uri("/mapping/non-associations/non-association-id/{id}", id)
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
