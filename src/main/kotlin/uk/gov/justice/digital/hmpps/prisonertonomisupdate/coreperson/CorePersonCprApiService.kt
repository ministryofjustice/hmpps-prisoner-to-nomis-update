package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.api.PrisonApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.api.SysconSyncApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.DpsPrisonRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligionReadResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CorePersonCprApiService(
  corePersonApiWebClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", this::class.java.simpleName),
  )

  private val personApi = PrisonApi(corePersonApiWebClient)
  private val syncApi = SysconSyncApi(corePersonApiWebClient)

  suspend fun getCorePerson(prisonNumber: String): DpsPrisonRecord? = personApi
    .prepare(personApi.getByPrisonNumberDpsRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullForNotFound(backoffSpec)

  suspend fun getReligion(prisonNumber: String, cprReligionId: String): PrisonReligionReadResponse = syncApi
    .getPrisonReligion(prisonNumber, cprReligionId)
    .awaitSingle()

  suspend fun getPrisonerContact(prisonNumber: String, cprContactId: String): PrisonContact = syncApi
    .getPrisonerContact(prisonNumber, cprContactId)
    .awaitSingle()
}
