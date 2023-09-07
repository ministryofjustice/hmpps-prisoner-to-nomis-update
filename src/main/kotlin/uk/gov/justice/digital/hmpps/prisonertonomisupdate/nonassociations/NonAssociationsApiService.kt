package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.LegacyNonAssociation

@Service
class NonAssociationsApiService(private val nonAssociationsApiWebClient: WebClient) {

  suspend fun getNonAssociation(id: Long): LegacyNonAssociation =
    nonAssociationsApiWebClient.get()
      .uri("/legacy/api/non-associations/$id")
      .retrieve()
      .awaitBody()
}

enum class CreatingSystem {
  NOMIS,
  DPS,
}
