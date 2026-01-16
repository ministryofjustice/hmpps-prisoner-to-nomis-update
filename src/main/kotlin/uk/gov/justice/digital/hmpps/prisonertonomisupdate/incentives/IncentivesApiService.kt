package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.api.IncentiveLevelsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.api.MaintainIncentiveReviewsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.api.PrisonIncentiveLevelsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveReviewSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.PrisonIncentiveLevel

@Service
class IncentivesApiService(@Qualifier("incentivesApiWebClient") webClient: WebClient) {
  private val maintainIncentiveReviewsApi = MaintainIncentiveReviewsApi(webClient)
  private val incentiveLevelsApi = IncentiveLevelsApi(webClient)
  private val prisonIncentiveLevelsApi = PrisonIncentiveLevelsApi(webClient)

  suspend fun getIncentive(incentiveId: Long): IncentiveReviewDetail = maintainIncentiveReviewsApi.getIncentiveReviewById(incentiveId).awaitSingle()

  suspend fun getCurrentIncentive(bookingId: Long): IncentiveReviewSummary? = maintainIncentiveReviewsApi.prepare(
    maintainIncentiveReviewsApi.getPrisonerIncentiveLevelHistoryRequestConfig(bookingId, withDetails = false),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getGlobalIncentiveLevel(incentiveLevelCode: String): IncentiveLevel = incentiveLevelsApi
    .getIncentiveLevel(incentiveLevelCode).awaitSingle()

  suspend fun getGlobalIncentiveLevelsInOrder(): List<IncentiveLevel> = incentiveLevelsApi
    .getIncentiveLevels(withInactive = true).awaitSingle()

  suspend fun getPrisonIncentiveLevel(prisonId: String, incentiveLevelCode: String): PrisonIncentiveLevel = prisonIncentiveLevelsApi
    .getPrisonIncentiveLevel(prisonId, incentiveLevelCode).awaitSingle()
}

fun List<IncentiveLevel>.toCodeList(): List<String> = this.map { it.code }
