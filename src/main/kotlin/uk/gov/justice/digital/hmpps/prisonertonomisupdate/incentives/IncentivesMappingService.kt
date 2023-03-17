package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNotFound
import java.time.LocalDateTime

@Service
class IncentivesMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: IncentiveMappingDto) {
    webClient.post()
      .uri("/mapping/incentives")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenIncentiveIdOrNull(incentiveId: Long): IncentiveMappingDto? =
    webClient.get()
      .uri("/mapping/incentives/incentive-id/$incentiveId")
      .retrieve()
      .awaitBodyOrNotFound()
}

data class IncentiveMappingDto(
  val nomisBookingId: Long,
  val nomisIncentiveSequence: Int,
  val incentiveId: Long,
  val label: String? = null,
  val mappingType: String,
  val whenCreated: LocalDateTime? = null,
)
