package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.awaitBodyOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.api.IncentiveLevelsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.api.MaintainIncentiveReviewsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.api.PrisonIncentiveLevelsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveReviewSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.PrisonIncentiveLevel

@Service
class IncentivesApiService(@Qualifier("incentivesApiWebClient") private val webClient: WebClient) {
  private val maintainIncentiveReviewsApi: MaintainIncentiveReviewsApi = MaintainIncentiveReviewsApi(webClient)
  private val incentiveLevelsApi: IncentiveLevelsApi = IncentiveLevelsApi(webClient)
  private val prisonIncentiveLevelsApi: PrisonIncentiveLevelsApi = PrisonIncentiveLevelsApi(webClient)

  suspend fun getIncentive(incentiveId: Long): IncentiveReviewDetail = maintainIncentiveReviewsApi.prepare(
    maintainIncentiveReviewsApi.getIncentiveReviewByIdRequestConfig(incentiveId),
  )
    .retrieve()
    .awaitBody()

  suspend fun getCurrentIncentive(bookingId: Long): IncentiveReviewSummary? = maintainIncentiveReviewsApi.prepare(
    maintainIncentiveReviewsApi.getPrisonerIncentiveLevelHistoryRequestConfig(bookingId, withDetails = false),
  )
    .retrieve()
    .awaitBodyOrNullForNotFound()

  suspend fun getGlobalIncentiveLevel(incentiveLevelCode: String): IncentiveLevel = incentiveLevelsApi.prepare(
    incentiveLevelsApi.getIncentiveLevelRequestConfig(incentiveLevelCode),
  )
    .retrieve()
    .awaitBody()

  suspend fun getGlobalIncentiveLevelsInOrder(): List<IncentiveLevel> = incentiveLevelsApi.prepare(
    incentiveLevelsApi.getIncentiveLevelsRequestConfig(withInactive = true),
  )
    .retrieve()
    .awaitBody()

  suspend fun getPrisonIncentiveLevel(prisonId: String, incentiveLevelCode: String): PrisonIncentiveLevel = prisonIncentiveLevelsApi.prepare(
    prisonIncentiveLevelsApi.getPrisonIncentiveLevelRequestConfig(prisonId, incentiveLevelCode),
  )
    .retrieve()
    .awaitBody()
}

fun List<IncentiveLevel>.toCodeList(): List<String> = this.map { it.code }
