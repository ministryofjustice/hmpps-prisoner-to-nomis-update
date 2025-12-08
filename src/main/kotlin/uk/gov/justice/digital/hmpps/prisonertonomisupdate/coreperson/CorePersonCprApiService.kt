package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.api.HMPPSPersonAPIApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound

@Service
class CorePersonCprApiService(
  corePersonApiWebClient: WebClient,
) {
  private val personApi = HMPPSPersonAPIApi(corePersonApiWebClient)

  suspend fun getCorePerson(prisonNumber: String): CanonicalRecord? = personApi
    .prepare(personApi.getRecord1RequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullForNotFound()
}
