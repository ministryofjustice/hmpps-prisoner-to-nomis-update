package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodilessEntityOrThrowOnConflict
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNotFound
import java.time.LocalDateTime

@Service
class HearingsMappingService(
  @Qualifier("mappingWebClient") private val webClient: WebClient,
) {

  suspend fun createMapping(request: AdjudicationHearingMappingDto) {
    webClient.post()
      .uri("/mapping/hearings")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrThrowOnConflict()
  }

  suspend fun getMappingGivenDpsHearingIdOrNull(dpsHearingId: String): AdjudicationHearingMappingDto? =
    webClient.get()
      .uri("/mapping/hearings/dps/$dpsHearingId")
      .retrieve()
      .awaitBodyOrNotFound()

  suspend fun getMappingGivenDpsHearingId(dpsHearingId: String): AdjudicationHearingMappingDto =
    webClient.get()
      .uri("/mapping/hearings/dps/$dpsHearingId")
      .retrieve()
      .awaitBody()

  suspend fun deleteMappingGivenDpsHearingId(dpsHearingId: String): Unit =
    webClient.delete()
      .uri("/mapping/hearings/dps/$dpsHearingId")
      .retrieve()
      .awaitBody()
}

class AdjudicationHearingMappingDto(
  val nomisHearingId: Long,
  val dpsHearingId: String,
  val label: String? = null,
  val mappingType: String = "ADJUDICATION_CREATED",
  val whenCreated: LocalDateTime? = null,
  mappingEntity: AdjudicationMappingEntity = AdjudicationMappingEntity.HEARING,
) : BaseAdjudicationMappingDto(mappingEntity)
