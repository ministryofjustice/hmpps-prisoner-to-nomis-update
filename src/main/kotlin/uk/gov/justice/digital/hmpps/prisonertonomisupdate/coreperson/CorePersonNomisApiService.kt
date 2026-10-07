package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.context.Context
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.CorePersonResourceApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonInsertReligionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonMergeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOffenderPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOffenderPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@Service
class CorePersonNomisApiService(
  @Qualifier("nomisApiWebClient") webClient: WebClient,
  retryApiService: RetryApiService,
) {
  private val backoffSpec = retryApiService.getBackoffSpec().withRetryContext(
    Context.of("api", this::class.java.simpleName),
  )

  private val api = CorePersonResourceApi(webClient)

  suspend fun getPrisonerForReconciliation(prisonNumber: String): CorePerson? = api
    .prepare(api.getOffenderForReconciliationRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullForNotFound(retrySpec = backoffSpec)

  suspend fun getPrisonerReligions(prisonNumber: String): List<OffenderBelief>? = api
    .prepare(api.getOffenderReligionsByPrisonNumberRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullForNotFound(retrySpec = backoffSpec)

  suspend fun mergeReligions(toPrisonNumber: String, corePersonMergeRequest: CorePersonMergeRequest) {
    api.updateOffenderByPrisonNumberAfterMerge(
      prisonNumber = toPrisonNumber,
      corePersonMergeRequest = corePersonMergeRequest,
    ).awaitSingleOrNull()
  }

  suspend fun insertReligion(toPrisonNumber: String, corePersonInsertReligionRequest: CorePersonInsertReligionRequest): Long = api.insertOffenderReligion(
    prisonNumber = toPrisonNumber,
    corePersonInsertReligionRequest = corePersonInsertReligionRequest,
  ).awaitSingle()

  suspend fun createOffenderPhone(prisonNumber: String, request: CreateOffenderPhoneRequest): CreateOffenderPhoneResponse = api
    .createOffenderPhone(prisonNumber, request)
    .awaitSingle()

  suspend fun createOffenderAddressPhone(prisonNumber: String, addressId: Long, request: CreateOffenderPhoneRequest): CreateOffenderPhoneResponse = api
    .createOffenderAddressPhone(prisonNumber, addressId, request)
    .awaitSingle()

  suspend fun createOffenderEmail(prisonNumber: String, request: CreateOffenderEmailRequest): CreateOffenderEmailResponse = api
    .createOffenderEmail(prisonNumber, request)
    .awaitSingle()

  suspend fun updateOffenderPhone(prisonNumber: String, phoneId: Long, request: UpdateOffenderPhoneRequest) {
    api.updateOffenderPhone(prisonNumber, phoneId, request).awaitSingleOrNull()
  }

  suspend fun updateOffenderAddressPhone(prisonNumber: String, addressId: Long, phoneId: Long, request: UpdateOffenderPhoneRequest) {
    api.updateOffenderAddressPhone(prisonNumber, addressId, phoneId, request).awaitSingleOrNull()
  }

  suspend fun updateOffenderEmail(prisonNumber: String, emailAddressId: Long, request: UpdateOffenderEmailRequest) {
    api.updateOffenderEmail(prisonNumber, emailAddressId, request).awaitSingleOrNull()
  }
}
