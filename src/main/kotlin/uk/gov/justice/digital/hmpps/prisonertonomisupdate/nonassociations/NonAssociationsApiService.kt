package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.LegacyNonAssociation

@Service
class NonAssociationsApiService(@Qualifier("nonAssociationsApiWebClient") private val webClient: WebClient) {

  suspend fun getNonAssociation(id: Long) =
    webClient.get()
      .uri("/legacy/api/non-associations/$id")
      .retrieve()
      .bodyToMono(LegacyNonAssociation::class.java)
      .awaitSingle()
}
